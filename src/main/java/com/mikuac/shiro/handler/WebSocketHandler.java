package com.mikuac.shiro.handler;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.core.BotFactory;
import com.mikuac.shiro.core.CoreEvent;
import com.mikuac.shiro.properties.WebSocketProperties;
import com.mikuac.shiro.task.ShiroAsyncTask;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.io.IOException;
import java.util.Optional;

/**
 * Created on 2021/7/16.
 *
 * @author Zero
 * @version $Id: $Id
 */
@Slf4j
public class WebSocketHandler extends TextWebSocketHandler {

    private static final String API_RESULT_KEY = "echo";

    private static final String FAILED_STATUS = "failed";

    private static final String RESULT_STATUS_KEY = "status";

    private final EventHandler eventHandler;

    private final BotFactory botFactory;

    private final ActionHandler actionHandler;

    private final ShiroAsyncTask shiroAsyncTask;

    private final BotContainer botContainer;

    private WebSocketProperties webSocketProperties;

    @Autowired
    public void setWebSocketProperties(WebSocketProperties webSocketProperties) {
        this.webSocketProperties = webSocketProperties;
    }

    private CoreEvent coreEvent;

    @Autowired
    public void setCoreEvent(CoreEvent coreEvent) {
        this.coreEvent = coreEvent;
    }

    /**
     * 构造函数
     *
     * @param eventHandler   {@link EventHandler}
     * @param botFactory     {@link BotFactory}
     * @param actionHandler  {@link ActionHandler}
     * @param shiroAsyncTask {@link ShiroAsyncTask}
     * @param botContainer   {@link BotContainer}
     */
    public WebSocketHandler(EventHandler eventHandler, BotFactory botFactory, ActionHandler actionHandler, ShiroAsyncTask shiroAsyncTask, BotContainer botContainer) {
        this.eventHandler = eventHandler;
        this.botFactory = botFactory;
        this.actionHandler = actionHandler;
        this.shiroAsyncTask = shiroAsyncTask;
        this.botContainer = botContainer;
    }

    /**
     * 获取连接的 QQ 号
     *
     * @param session {@link WebSocketSession}
     * @return QQ 号
     */
    private long parseSelfId(WebSocketSession session) {
        Optional<String> opt = Optional.ofNullable(session.getHandshakeHeaders().getFirst("x-self-id"));
        return opt.map(botId -> {
            try {
                return Long.parseLong(botId);
            } catch (NumberFormatException e) {
                return 0L;
            }
        }).orElse(0L);
    }

    /**
     * 访问密钥检查
     *
     * @param session WebSocketSession
     * @return 是否验证通过
     */
    private boolean checkToken(WebSocketSession session) {
        String token = webSocketProperties.getAccessToken();
        if (token.isEmpty()) {
            return true;
        }
        String clientToken = session.getHandshakeHeaders().getFirst("authorization");
        log.debug("Access Token: {}", clientToken);
        if (clientToken == null || clientToken.isEmpty()) {
            return false;
        }
        return token.equals(clientToken);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        try {
            long xSelfId = parseSelfId(session);
            if (xSelfId == 0L) {
                log.error("Account get failed for client");
                session.close();
                return;
            }
            if (!checkToken(session)) {
                log.error("Access token invalid");
                session.close();
                return;
            }
            if (!coreEvent.session(session)) {
                session.close();
                return;
            }
            Bot bot = botFactory.createBot(xSelfId, session);
            botContainer.robots.put(xSelfId, bot);
            log.info("Account {} connected", xSelfId);
            coreEvent.online(bot);
        } catch (IOException e) {
            log.error("Websocket session close exception: {}", e.getMessage(), e);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        long xSelfId = parseSelfId(session);
        if (xSelfId == 0L) {
            return;
        }
        if (botContainer.robots.containsKey(xSelfId)) {
            botContainer.robots.remove(xSelfId);
            log.warn("Account {} disconnected", xSelfId);
            coreEvent.offline(xSelfId);
        }
    }

    /**
     * {@inheritDoc}
     */
    @Override
    protected void handleTextMessage(@NonNull WebSocketSession session, TextMessage message) {
        long xSelfId = parseSelfId(session);
        JSONObject result = JSON.parseObject(message.getPayload());
        log.debug("[Event] {}", result.toJSONString());
        // if resp contains echo field, this resp is action resp, else event resp.
        if (result.containsKey(API_RESULT_KEY)) {
            if (FAILED_STATUS.equals(result.get(RESULT_STATUS_KEY))) {
                log.error("Action failed: {}", result.get("wording"));
            }
            actionHandler.onReceiveActionResp(result);
        } else {
            shiroAsyncTask.execHandlerMsg(eventHandler, xSelfId, result);
        }
    }

}
