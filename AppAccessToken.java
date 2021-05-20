package com.yunzhijia.teamwork.cloud.factory.impl;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.kingdee.cbos.common.utils.http.HttpHelper;
import com.yunzhijia.teamwork.base.Person;
import com.yunzhijia.teamwork.cloud.CloudConfig;
import com.yunzhijia.teamwork.cloud.factory.AccessToken;
import com.yunzhijia.teamwork.cloud.factory.BasetToken;
import com.yunzhijia.teamwork.cloud.factory.OPerson;
import com.yunzhijia.teamwork.exception.BussinessException;
import com.yunzhijia.teamwork.redis.RedisUtil;
import com.yunzhijia.teamwork.task.TodoMessageDTO;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;


/**
 * @ClassName AppAccessToken
 * @Description: TODO
 * @Author Administrator
 * @Date 2020/7/29 13 42
 * @Version V1.0
 **/
@Slf4j
public class AppAccessToken extends BasetToken implements AccessToken {

//    protected log log = logFactory.getlog(this.getClass());

    private OkHttpClient client = new OkHttpClient().newBuilder()
            .connectTimeout(60, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build();


    private static final String REDIS_EXPIRED_UNTIL = "AppExpiredUntil";

    private CloudConfig cloudConfig;

    private RedisUtil redisUtil;

    public AppAccessToken(CloudConfig cloudConfig, RedisUtil redisUtil) {
        this.cloudConfig = cloudConfig;
        this.redisUtil = redisUtil;
    }


    @Override
    public void getAccesssToken(String appId) throws Exception {

        log.info("获取到accessToken请求参数secret:\n" + cloudConfig.appSecret);
        JSONObject param = new JSONObject();
        param.put("appId", appId);
        param.put("secret", cloudConfig.appSecret);
        param.put("timestamp", System.currentTimeMillis());
        param.put("scope", "app");

        Map<String,String> headers=new HashMap<>();
        headers.put("Content-Type","application/json");

        String url = cloudConfig.outHost + "/gateway/oauth2/token/getAccessToken";
        String retString = HttpHelper.post(headers,param.toJSONString(), url,10000);

        JSONObject jsonObject = JSON.parseObject(retString);

        log.info("获取到accessToken:\n" + jsonObject);
        getParam(jsonObject);
        redisUtil.set(appId+"expiredUntil", expiredUntil+"", expiredUntil);
        redisUtil.set(appId+"accessToken", accessToken, expiredUntil);
    }

    @Override
    public void refreshAccessToken(String appId) throws Exception {
        JSONObject param = new JSONObject();
        param.put("appId", appId);
        param.put("refreshToken", refreshToken);
        param.put("timestamp", System.currentTimeMillis());
        param.put("scope", "app");

        Map<String,String> headers=new HashMap<>();
        headers.put("Content-Type","application/json");

        String url = cloudConfig.outHost + "/gateway/oauth2/token/refreshToken";
        String retString = HttpHelper.post(headers, param.toJSONString(),url,10000);

        JSONObject jsonObject = JSON.parseObject(retString);
        log.info("刷新accessToken:\n" + jsonObject);
        getParam(jsonObject);
        redisUtil.set(appId+"expiredUntil", expiredUntil+"", expiredUntil);
        redisUtil.set(appId+"accessToken", accessToken, expiredUntil);
    }

    @Override
    public void checkAndRefreshAccessToken(String appId) throws Exception {
        if(redisUtil.exists(appId+"expiredUntil")) {
            expiredUntil = Long.parseLong(redisUtil.get(appId+"expiredUntil"));
            accessToken = redisUtil.get(appId+"accessToken");
            if(expiredUntil<60 && expiredUntil >0) {
                refreshAccessToken(appId);
            }
            if(expiredUntil==0) {
                getAccesssToken(appId);
            }
        }else {
            getAccesssToken(appId);
        }
    }

    public JSONObject generatetodo(TodoMessageDTO todoMessageDTO) throws Exception {
        checkAndRefreshAccessToken(todoMessageDTO.getAppId());
//        todoMessageDTO.setAppId(cloudConfig.appId);

        Map<String,String> headers=new HashMap<>();
        headers.put("Content-Type","application/json");

        todoMessageDTO.setHeadImg("https://www.yunzhijia.com/space/c/photo/load?id=5a2f7ad750f8dd7810e79981");
        String url = cloudConfig.outHost + "/gateway/newtodo/open/generatetodo.json?accessToken=";
        url += accessToken;
        String retString = HttpHelper.post(headers, JSON.toJSONString(todoMessageDTO),url,10000);
        JSONObject jsonObject = JSON.parseObject(retString);
        log.info("推送待办信息:\n" + jsonObject);
        return jsonObject;
    }


    public JSONObject action(TodoMessageDTO todoMessageDTO) throws Exception {

//        checkAndRefreshAccessToken(todoMessageDTO.getAppId());

        Map<String, Object> param = new HashMap<>();
        param.put("sourcetype", todoMessageDTO.getAppId());
        Map<String, Integer> todo = new HashMap<>();
        todo.put("deal", 1);
        todo.put("read", 1);
        param.put("actiontype", todo);
        param.put("sourceitemid",todoMessageDTO.getSourceId());
        param.put("openids", todoMessageDTO.getOpenids());
        checkAndRefreshAccessToken(todoMessageDTO.getAppId());

        Map<String,String> headers=new HashMap<>();
        headers.put("Content-Type","application/json");

        String url = cloudConfig.outHost + "/gateway/newtodo/open/action.json?accessToken=";
        url += accessToken;
        String retString = HttpHelper.post(headers, JSON.toJSONString(param),url,10000);
        JSONObject jsonObject = JSON.parseObject(retString);
        log.info("推送待办转已办信息:\n" + jsonObject);
        return jsonObject;
    }

    public void send(TodoMessageDTO todoMessageDTO) throws Exception {
        //短信发送
        Map<String, String> param = new HashMap<>();
        Map<String,Object> dataMap = new HashMap<>();
        dataMap.put("service","todoRemind");
        dataMap.put("targets",new String[]{todoMessageDTO.getPhone()});
        Map<String,Object> messageMap = new HashMap<>();
        messageMap.put("sender",todoMessageDTO.getContent());
        messageMap.put("title", todoMessageDTO.getTitle());

        dataMap.put("messageMap",messageMap);
        dataMap.put("network", todoMessageDTO.getEid());
        Map<String,Object> senderMap = new HashMap<>();
        senderMap.put("openId",todoMessageDTO.getSendOpenId());

        dataMap.put("senderMap",senderMap);

        param.put("data", JSON.toJSONString(dataMap));
        String url = cloudConfig.innerHost + "/smsgateway/sms/send";
        log.info("短信发送参数"+ JSON.toJSONString(param));
        String post = HttpHelper.post(null, param, url, 10000);
        log.info("短信返回参数"+ post);
    }

    public JSONObject getCtrlInfo(String eid) throws Exception {
        //短信发送
        Map<String, String> param = new HashMap<>();
        param.put("serviceId", "S100254");
        param.put("eid", eid);

//        param.put("serviceId", "S100048");
//        param.put("eid", "100059");
        String url = cloudConfig.innerHost + "/scc/service/getCtrlInfo";
        log.info("获取服务控制信息"+ JSON.toJSONString(param));
        String post = HttpHelper.post(null, param, url, 10000);
        log.info("获取服务控制信息返回参数"+ post);

        JSONObject jsonObject = JSON.parseObject(post);
        log.info("获取用户实例:\n" + jsonObject);
        if (jsonObject.getBoolean("success")) {
            return jsonObject;
        } else {
            throw new BussinessException(jsonObject.getString("error"));
        }
    }


    public Person acquirecontext(String ticket, String appId) throws Exception {
        Map<String,String> headers=new HashMap<>();
        headers.put("Content-Type","application/json");

        checkAndRefreshAccessToken(appId);
        Map<String, Object> param = new HashMap<>();
        param.put("appid", appId);
        param.put("ticket", ticket);
        String url = cloudConfig.outHost + "/gateway/ticket/user/acquirecontext?accessToken=";
        url += accessToken;
        Person person = null;
        String retString = HttpHelper.post(headers, JSON.toJSONString(param),url,10000);
        JSONObject jsonObject = JSON.parseObject(retString);
        if (jsonObject.getBoolean("success")) {
            person = JSON.parseObject(jsonObject.getString("data"), Person.class);
        } else {
            log.error("报错"+ retString);
            getAccesssToken(appId);
            url = cloudConfig.outHost + "/gateway/ticket/user/acquirecontext?accessToken=";
            url += accessToken;
            retString = HttpHelper.post(headers, JSON.toJSONString(param),url,10000);
            jsonObject = JSON.parseObject(retString);
            if (jsonObject.getBoolean("success")) {
                person = JSON.parseObject(jsonObject.getString("data"), Person.class);
            }else {
                cloudConfig.setAppSecret("teamwork_saas");
                throw new BussinessException(jsonObject.getString("error"));
            }
        }
        log.info("解析ticket包含的人员信息:\n" + retString);

        return person;
    }


    public List<OPerson> getUserInfoRelyOrgIdsOld(String orgIds, String eid, String appId) throws Exception {
        checkAndRefreshAccessToken(appId);

        Map<String,String> headers=new HashMap<>();
        headers.put("Content-Type","application/json");
        Map<String, String> param = new HashMap<>();
        int begin = 0;
        param.put("begin", begin+"");
        param.put("count", 500+"");
        param.put("eid", eid);
        param.put("isIncludeSub", "true");
        param.put("orgIds", orgIds);
        String url = cloudConfig.outHost + "/gateway/opendata-control/data/org/getUserInfoRelyOrgIds?accessToken=";
        url += accessToken;
        String retString = null;
        List<OPerson> oPersonList = null;
        log.info("获取部门下人员传参:\n" + JSON.toJSONString(param));
        log.info("获取部门下人员传参:\n" + url);
        retString = HttpHelper.post(null, param,url,10000);
//            retString = HttpClientUtils.doPost(url, param);
        log.info("获取部门下人员信息:\n" + retString);
        boolean hasUser = true;

        JSONObject jsonObject = JSON.parseObject(retString);
        if (jsonObject.getBoolean("success")) {
            JSONArray data = jsonObject.getJSONArray("data");
            if (!data.isEmpty()) {
                if (data.size() < 500) {
                    hasUser = false;
                }
                oPersonList = JSON.parseArray(jsonObject.getString("data"), OPerson.class);

            }
        }

        while (hasUser) {
            begin = begin + 1;
            param.put("begin", begin * 500+"");
            retString = HttpHelper.post(null, param,url,10000);
            jsonObject = JSON.parseObject(retString);
            if (jsonObject.getBoolean("success")) {
                JSONArray data = jsonObject.getJSONArray("data");
                if (!data.isEmpty()) {
                    if (data.size() < 500) {
                        hasUser = false;
                    }
                    List<OPerson> oPersonList1 = JSON.parseArray(jsonObject.getString("data"), OPerson.class);
                    oPersonList.addAll(oPersonList1);

                } else {
                    hasUser = false;
                }
            }
        }
        return oPersonList;
    }

    public JSONObject getUserInfoRelyOrgIds(int begin,String orgIds, String eid) throws Exception {

        Map<String,String> headers=new HashMap<>();
        headers.put("Content-Type","application/json");
        Map<String, String> param = new HashMap<>();
//        int begin = 0;
        param.put("pageNum", begin+"");
        param.put("pageSize", 100+"");
        param.put("eid", eid);
        param.put("isIncludeSub", "true");
        param.put("orgIds", orgIds);
        String url = cloudConfig.outHost + "/openorg/user/getPersonsByEidAndOrgIdsAndPageForNotice";
        String retString = null;
        JSONArray jsonArray = null;
        log.info("获取部门下人员传参:\n" + JSON.toJSONString(param));
        log.info("获取部门下人员传参:\n" + url);
        retString = HttpHelper.post(null, param,url,10000);

//        retString= "{\"success\":true,\"error\":null,\"errorCode\":100,\"data\":[]}";

//            retString = HttpClientUtils.doPost(url, param);
        log.info("获取部门下人员信息:\n" + retString);
        JSONObject jsonObject = JSON.parseObject(retString);

        return jsonObject;
    }

    public  static JSONArray getserInfoRelyOrgIds() throws Exception {

//        Map<String,String> headers=new HashMap<>();
//        headers.put("Content-Type","application/json");
//        Map<String, String> param = new HashMap<>();
////        int begin = 0;
//        param.put("pageNum", begin+"");
//        param.put("pageSize", 100+"");
//        param.put("eid", eid);
//        param.put("isIncludeSub", "true");
//        param.put("orgIds", orgIds);
//        String url = cloudConfig.outHost + "/openorg/user/getPersonsByEidAndOrgIdsAndPageForNotice";
//        String retString = null;
//        JSONArray jsonArray = null;
//        log.info("获取部门下人员传参:\n" + JSON.toJSONString(param));
//        log.info("获取部门下人员传参:\n" + url);
//        retString = HttpHelper.post(null, param,url,10000);
        boolean hasUser = true;
        String retString= "{\"success\":true,\"error\":null,\"errorCode\":100,\"data\":[{\"phone\":\"13927465725\",\"openId\":\"5ecb95f4e4b0fb9384d14d4b\",\"oid\":\"60377049a2bb4c00015a3c09\",\"userName\":\"万鹏程\",\"wbUserId\":\"5ecb95f4e4b0fb9384d14d4b\"},{\"phone\":\"15080647021\",\"openId\":\"5f81789fe4b05e8d4d22a53f\",\"oid\":\"603df6f5e4b0b556212334d3\",\"userName\":\"田甜\",\"wbUserId\":\"5f81789fe4b05e8d4d22a53f\"}]}";

//            retString = HttpClientUtils.doPost(url, param);
        log.info("获取部门下人员信息:\n" + retString);
        JSONObject jsonObject = JSON.parseObject(retString);
        if (jsonObject.getBoolean("success")) {
            JSONArray data = jsonObject.getJSONArray("data");
            if (!data.isEmpty()) {
                if (data.size() < 100) {
                    hasUser = false;
                }
//                jsonArray = JSON.parseArray(jsonObject.getString("data"));
            } else {
                hasUser = false;
            }
        }
        return null;
    }

    public static void main(String[] args) throws Exception {
        JSONArray jsonArray = getserInfoRelyOrgIds();
    }

    public OPerson getperson(String openId, String eid, String appId, String ticket) throws Exception {
        Map<String,String> headers=new HashMap<>();
        headers.put("Content-Type","application/json");

        Map<String, String> param = new HashMap<>();
        checkAndRefreshAccessToken(appId);
        param.put("eid", eid);
        param.put("openId", openId);
        String url = cloudConfig.outHost + "/gateway/opendata-control/data/getperson?accessToken=";
        url += accessToken;
        String retString = null;
        OPerson oPerson = null;
        retString = HttpHelper.post(null, param, url,10000);
        log.info("获取人员信息:\n" + retString);
//            retString = HttpClientUtils.doPost(url, param);
        JSONObject jsonObject = JSON.parseObject(retString);
        if (jsonObject.getBoolean("success")) {
            JSONArray data = jsonObject.getJSONArray("data");
            if (!data.isEmpty()) {
                String s = JSON.toJSONString(data.get(0));
                oPerson = JSON.parseObject(s, OPerson.class);
            }
        } else {
            String redictUrl = "/appmanage/pages/feeapp/index.html?logo=aHR0cDovL3d3dy55dW56aGlqaWEuY29tL21jbG91ZC9kb3dubG9hZC5hY3Rpb24/ZmlsZW5hbWU9MTEwMDAucG5nJnR5cGU9MSZ0PTE2MDM5NTI3MDcwMDBfNjQ0Mg==&state=4&appname=%E6%96%87%E4%BB%B6%E4%BC%A0%E9%98%85&ticket="+ticket+"&client_id="+appId+"&expire_time="+System.currentTimeMillis();
            throw new BussinessException(jsonObject.getString("error"),"403",redictUrl);
        }
        return oPerson;
    }

    public JSONObject getUserInfo(String openId, String eid) throws Exception {

        Map<String, String> param = new HashMap<>();
        param.put("eid", eid);
        param.put("oids", openId);
        String url = cloudConfig.outHost+"/openorg/person/getPersonsByOidsWithNoToken";
        String response =null;
        response = HttpHelper.post(null, param, url,10000);

        JSONObject jsonObject = JSON.parseObject(response);
        log.info("获取用户实例:\n" + jsonObject);
        if (jsonObject.getBoolean("success")) {
            return jsonObject;
        } else {
            throw new BussinessException(jsonObject.getString("error"));
        }
    }

        /**
     * 文件上传
     * <p>
     * https://yunzhijia.com/cloudflow-openplatform/fileUploadAndDownload/4002
     *
     * @return
     */
    public JSONObject uploadFile(String filePath, String eid,String fileName) {

        String url = cloudConfig.outHost + "/docrest/file/uploadfile";
        byte[] bytes;
        try {
            bytes = Files.readAllBytes(Paths.get(filePath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }


        MediaType fileType = MediaType.parse("application/octet-stream");
        RequestBody fileBody = RequestBody.create(fileType, bytes);
        RequestBody body = new MultipartBody.Builder()
                .addFormDataPart("file", fileName, fileBody)
                .addFormDataPart("bizkey", "cloudflow")
                .addFormDataPart("networkId", eid)
                .build();
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .addHeader("content-type", "multipart/form-data")
                .addHeader("Cache-Control", "no-cache")
//                .addHeader("x-accessToken", accessToken)
                .build();

        try {
            Response response = client.newCall(request).execute();
            JSONObject jsonObject = JSON.parseObject(response.body().string());
            System.out.println("文件上传\n" + jsonObject);
            return jsonObject;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * 文件下载
     * <p>
     * https://yunzhijia.com/cloudflow-openplatform/fileUploadAndDownload/4003
     *
     * @param fileId
     */
    public Response downloadFile(String fileId) {
        String url = cloudConfig.outHost + "/docrest/file/downloadfile/";
        url += fileId;

        Request request = new Request.Builder()
                .url(url)
                .get()
//                .addHeader("x-accessToken", accessToken)
                .addHeader("Cache-Control", "no-cache")
                .build();

        try {
            Response response = client.newCall(request).execute();
//            try (InputStream is = response.body().byteStream();
//                 FileOutputStream fos = new FileOutputStream("download.txt")) {
//                int length;
//                byte[] buf = new byte[2048];
//                while ((length = is.read(buf)) != -1) {
//                    fos.write(buf, 0, length);
//                }
//                fos.flush();
//            }
            return response;
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
//        return null;
    }

}
