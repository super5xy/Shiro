package com.mikuac.shiro.task;

import com.alibaba.fastjson2.JSONObject;
import com.mikuac.shiro.core.Bot;
import com.mikuac.shiro.core.BotContainer;
import com.mikuac.shiro.handler.EventHandler;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * 异步任务
 *
 * @author zero
 * @version $Id: $Id
 */
@Component
public class ShiroAsyncTask {

    private BotContainer botContainer;

    @Autowired
    public void setBotContainer(BotContainer botContainer) {
        this.botContainer = botContainer;
    }

    /**
     * 事件上报处理函数
     *
     * @param event   {@link EventHandler}
     * @param xSelfId 机器人 QQ
     * @param resp    上报的 Json 数据
     */
    @Async("shiroTaskExecutor")
    public void execHandlerMsg(EventHandler event, long xSelfId, JSONObject resp) {
        Bot bot = botContainer.robots.get(xSelfId);
        if (bot != null) {
            event.handler(bot, resp);
        }
    }

}
