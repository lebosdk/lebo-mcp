package lebo.mcp;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.apache.logging.log4j.util.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.stereotype.Service;
import org.springframework.util.DigestUtils;
import org.springframework.web.client.RestClient;

import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.Map;
import java.util.TreeMap;

@Service
public class McpService {

    private static final Logger logger = LoggerFactory.getLogger(McpService.class);

    private static final String LEBO_BASE_URL = "https://saas.hpplay.cn/api/lebo-open/v2/";
//    private static final String TEST_LEBO_BASE_URL = "https://test01-saas.hpplay.cn/api/lebo-open/v2/";

    private final RestClient leboClient;

    public McpService() {
        this.leboClient = RestClient.builder().baseUrl(LEBO_BASE_URL).build();
    }

    public record CastOperateRequest(@JsonProperty("action") String action, @JsonProperty("appId") String appId,
                                     @JsonProperty("id") String id, @JsonProperty("operateType") String operateType,
                                     @JsonProperty("sessionId") String sessionId, @JsonProperty("uid") String uid,
                                     @JsonProperty("value") String value) {
    }


    @Tool(description = "播放控制, 例如 图片或者视频控制 (播放、暂停、停止、跳转、音量控制等)、 网页控制 (结束投屏、切换网页或打开别的网页等)。")
    public CommResult castOperate(@ToolParam(required = false, description = "平台应用ID") String appId, //
                                  @ToolParam(required = false, description = "平台secret") String secret, //

                                  @ToolParam(required = true, description = "播放控制: " +//
                                          "video(视频)、image(图片)控制 [ play(播放) | pause(暂停) | stop(停止) | seekto(跳转 秒) | volumeTo(设定音量 范围0-100) | volumeAdd(音量增加 负值为减少) ] 、" +//
                                          "webpage(网页)控制 [end(结束投屏) | replace(切换、打开网页) | thumbnail(查看缩略图) |  up：遥控器上健 | down(向下) | left(向左) | right(向右) ok(确认)]") String action,//

                                  @ToolParam(required = true, description = "操作类型: playControl [video(视频)、image(图片)]、eventControl [webpage(网页)]") String operateType,//
                                  @ToolParam(required = true, description = "会话ID: 本次会话唯一标识") String sessionId,//
                                  @ToolParam(required = false, description = "action值: action为volumeAdd时，value为-20,则表示音量减少20，当事件为replace时，value值为媒体文件url") String value

    ) throws Exception {

        String leboAppId = Strings.isBlank(appId) ? getLeboAppId() : appId;
        String leboSecretKey = Strings.isBlank(secret) ? getLeboSecretKey() : secret;

        if (Strings.isBlank(leboAppId) || Strings.isBlank(leboSecretKey)) {
            throw new Exception("平台应用ID或平台secret为空");
        }

//        logger.info("castOperate: action: {}  operateType: {} sessionId: {} value: {} leboAppId: {} leboSecretKey: {}", action, operateType, sessionId, value, leboAppId, leboSecretKey);

        String deviceId = CommContant.DEVICE_ID;
        TokenResult.Data tokenData = getToken(leboAppId, leboSecretKey, deviceId);

        String uid = tokenData.uid;
        String token = tokenData.token;

        String requestUrl = "/castOperate";

        CastOperateRequest request = new CastOperateRequest(action, leboAppId, sessionId, operateType, sessionId, uid, value);

        CommResult commResult = leboClient.post().uri(requestUrl).header("Authorization", "Bearer " + token)//
                .header("Content-Type", "application/json")//
                .body(request).retrieve().body(CommResult.class);

        return commResult;
    }

    /**
     * 推送图片、视频、网页链接
     *
     * @param tvCode
     * @param targetUID
     * @param targetAppId
     * @param mediaType
     * @param url
     * @param sessionId
     * @return
     * @throws Exception
     */
    @Tool(description = "推送、播放或打开图片、视频、网页等链接")
    public CommResult pushContent(@ToolParam(required = false, description = "平台应用ID") String appId, //
                                  @ToolParam(required = false, description = "平台secret") String secret, //

                                  @ToolParam(required = false, description = "投屏码: 与targetUID参数二选一，两组参数必填一组") String tvCode, //
                                  @ToolParam(required = false, description = "电视端uid: targetUID和targetAppId是一组参数, 与tvCode二选一，两组参数必填一组") String targetUID, //
                                  @ToolParam(required = false, description = "电视端appId: targetUID和targetAppId是一组参数") String targetAppId, //

                                  @ToolParam(required = true, description = "推送内容: video(视频)、image(图片)、webpage(网页)、audio(音频) ") String mediaType, //
                                  @ToolParam(required = true, description = "资源地址") String url, //
                                  @ToolParam(required = true, description = "会话ID: 本次会话唯一标识") String sessionId//
    ) throws Exception {

        String leboAppId = Strings.isBlank(appId) ? getLeboAppId() : appId;
        String leboSecretKey = Strings.isBlank(secret) ? getLeboSecretKey() : secret;

        if (Strings.isBlank(leboAppId) || Strings.isBlank(leboSecretKey)) {
            throw new Exception("平台应用ID或平台secret为空");
        }

        if (Strings.isBlank(tvCode) && Strings.isBlank(targetUID)) {
            throw new Exception("投屏码或者电视uid为空");
        }

//        logger.info("pushContent: tvCode:{} targetUID:{} targetAppId:{} mediaType:{} url:{} sessionId:{} leboAppId:{} leboSecretKey:{}", tvCode, targetUID, targetAppId, mediaType, url, sessionId, leboAppId, leboSecretKey);

        String deviceId = CommContant.DEVICE_ID;
        TokenResult.Data tokenData = getToken(leboAppId, leboSecretKey, deviceId);

        String uid = tokenData.uid;
        String token = tokenData.token;

        String body = """
                {
                	"playId": "1",
                	"action": "set-playlist",
                	"expired": 10000,
                	"playlist": [
                		{
                		"name": "",
                		"mediaType":"%s",
                		"urls": [
                			{
                				"id": "%s",
                				"resolution": "HD",
                				"url": "%s" ,
                				"height": 0,
                				"width": 0
                			}
                		]
                		}
                	]
                }
                """;

        body = String.format(body, mediaType, sessionId, url);

        String requestUrl = !Strings.isBlank(tvCode) ? "/push?tvCode=" + tvCode : "/push?targetAppId=" + targetAppId + "&targetUid=" + targetUID;
        requestUrl = requestUrl + "&appId=" + leboAppId + "&uid=" + uid + "&sessionId=" + sessionId;

        CommResult commResult = leboClient.post().uri(requestUrl).header("Authorization", "Bearer " + token)//
                .header("Content-Type", "application/json")//
                .body(body).retrieve().body(CommResult.class);

        return commResult;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CommResult(@JsonProperty("code") int code, @JsonProperty("data") Data data,
                             @JsonProperty("message") String message, @JsonProperty("requestId") String requestId) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Data() {
        }
    }

    public String getLeboSecretKey() {
        return System.getenv(CommContant.LEBO_SECRET_KEY);
    }

    public String getLeboAppId() {
        return System.getenv(CommContant.LEBO_APP_ID);
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record TokenResult(@JsonProperty("code") int code, @JsonProperty("data") Data data,
                              @JsonProperty("message") String message, @JsonProperty("requestId") String requestId) {
        @JsonIgnoreProperties(ignoreUnknown = true)
        public record Data(@JsonProperty("token") String token, @JsonProperty("uid") String uid,
                           @JsonProperty("expired") long expired) {
        }
    }


    /**
     * 获取token.
     *
     * @param appId
     * @param secret
     * @param deviceId
     * @return
     * @throws Exception
     */
    public TokenResult.Data getToken(String appId, String secret, String deviceId) throws Exception {

        Map<String, String[]> paramMap = new TreeMap<>();
        paramMap.put("appId", new String[]{appId});
        long currentTimeMillis = System.currentTimeMillis();
        paramMap.put("time", new String[]{currentTimeMillis + ""});
        paramMap.put("deviceId", new String[]{deviceId});
        Map<String, String> sortedParams = new TreeMap<>();
        for (Map.Entry<String, String[]> entry : paramMap.entrySet()) {
            String key = entry.getKey();
            if (!"sign".equals(key)) {
                String value = entry.getValue()[0];
                sortedParams.put(key, value);
            }
        }
        StringBuilder sortedParamStr = new StringBuilder();
        for (Map.Entry<String, String> entry : sortedParams.entrySet()) {
            String decodeStr = URLEncoder.encode(entry.getValue(), Charset.forName("UTF-8").name());
            sortedParamStr.append(entry.getKey()).append("=").append(decodeStr);
        }
        sortedParamStr.append(secret);

        String sign = DigestUtils.md5DigestAsHex(sortedParamStr.toString().getBytes(Charset.forName("UTF-8")));

        TokenResult tokenResult = leboClient.get().uri("/getToken?appId={appId}&deviceId={deviceId}&time={time}&sign={sign}", appId, deviceId, currentTimeMillis, sign).retrieve().body(TokenResult.class);

        if (tokenResult == null || tokenResult.code() != 0) {
//            logger.error("getToken: appId: {} deviceId: {} time: {} sign: {} tokenResult: {}", appId, deviceId, currentTimeMillis, sign, tokenResult);
            throw new Exception("获取token失败");
        }

        return tokenResult.data();
    }

    public static void main(String[] args) {
        McpService mcpService = new McpService();
        try {
            CommResult commResult = mcpService.pushContent(null, null, "2207929", null,//
                    null, "image", "http://gips3.baidu.com/it/u=3886271102,3123389489&fm=3028&app=3028&f=JPEG&fmt=auto?w=1280&h=960",//
                    "cast-image-68297929");
            CommResult castOperate = mcpService.castOperate(null, null, "stop", null, "123213", null);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}