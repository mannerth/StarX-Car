package org.mannerth.starxcar.realtime;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/** /api/realtime authenticates its first frame; credentials never appear in URLs. */
@Configuration
@EnableWebSocket
@io.swagger.v3.oas.annotations.tags.Tag(name = "实时通道", description = "WebSocket 首帧 JWT 鉴权、订单订阅与司机位置上报")
public class RealtimeConfig implements WebSocketConfigurer {
    private final RealtimeHandler handler;
    public RealtimeConfig(RealtimeHandler handler) { this.handler = handler; }
    @io.swagger.v3.oas.annotations.Operation(summary = "连接实时通道", description = "所有角色先发送 auth 首帧并提供 access JWT；只有 active 账号可使用。乘客/司机仅可订阅本人订单，只有司机可发送 driver.location。鉴权失败关闭连接，订单读取沿用现有归属校验和位置可见状态约束。")
    @Override public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        // No cookie authentication: all browser/mobile connections must supply an access JWT frame.
        registry.addHandler(handler, "/api/realtime").setAllowedOriginPatterns("*");
    }

    @org.springframework.context.annotation.Bean
    org.springdoc.core.customizers.OpenApiCustomizer realtimeOpenApi() {
        return api -> api.path("/api/realtime", new io.swagger.v3.oas.models.PathItem().get(
                new io.swagger.v3.oas.models.Operation().operationId("connectRealtime")
                        .tags(java.util.List.of("实时通道")).summary("升级为 WebSocket 实时通道")
                        .description("WebSocket Upgrade 握手后 10 秒内发送 {type:auth,token:<accessToken>}。JWT 不放 URL，refresh token 不可用于鉴权；账号必须 active。服务端返回 ready。客户端每 20 秒发送 ping，收到 pong。"
                                + "subscribe/unsubscribe 使用 topic 与 orderNo；order.detail 仅限订单乘客或已接单司机；order.location 仅限订单乘客且状态为 ACCEPTED/DRIVER_ARRIVED/IN_SERVICE；order.route 仅限乘客且 IN_SERVICE。"
                                + "snapshot 含 type/topic/orderNo/data，data 与对应 HTTP 查询 DTO 相同；subscription.error 含 code/message；位置尚未产生的 404 带 transient=true，订阅保留。"
                                + "订单离开位置/路线可见阶段时发送 subscription.closed/topic/orderNo/reason=resource_state_changed/code=409，正常结束对应订阅；订单详情订阅及连接继续工作。"
                                + "司机发送 {type:driver.location,id:<requestId>,data:<DriverLocationRequest>}，服务端绑定 JWT 身份、校验字段并复用现有位置写入；成功返回 ack/id，失败返回 error/id/code/message。"
                                + "业务成功提交后推送不含业务内容的 orders.changed；管理员还接收 location.changed。重连后需重新鉴权、订阅，获取最新快照；不会重放历史事件。JWT 失效关闭码 1008，账号不可用关闭码 1003。")
                        .responses(new io.swagger.v3.oas.models.responses.ApiResponses()
                                .addApiResponse("101", new io.swagger.v3.oas.models.responses.ApiResponse().description("已升级；后续鉴权与业务错误通过 JSON 消息返回"))
                                .addApiResponse("400", new io.swagger.v3.oas.models.responses.ApiResponse().description("不是有效的 WebSocket Upgrade 请求")))));
    }
}
