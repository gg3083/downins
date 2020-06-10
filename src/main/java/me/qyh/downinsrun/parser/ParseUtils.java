package me.qyh.downinsrun.parser;

import me.qyh.downinsrun.LogicException;
import me.qyh.downinsrun.Utils;
import me.qyh.downinsrun.encrypt.SealedBoxUtility;
import org.apache.http.NameValuePair;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.protocol.HttpClientContext;
import org.apache.http.cookie.Cookie;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.message.BasicNameValuePair;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

public class ParseUtils {

    private ParseUtils(){
        super();
    }

    public static Utils.ExpressionExecutor getSharedData(CloseableHttpClient client) throws IOException {
        String content = Https.toString(client,"https://www.instagram.com/data/shared_data/");
        return Utils.readJson(content);
    }

    public static void trySetSid(CloseableHttpClient client) throws Exception{
        trySetSid(client,true);
    }

    public static void trySetSid(CloseableHttpClient client,boolean exit) throws Exception {
        Utils.ExpressionExecutor executor = getSharedData(client);
        if(!executor.executeForExecutor("config->viewer").isNull()) {
            return;
        }

        DowninsConfig config = Configure.get().getConfig();
        String username = config.getUsername();
        String password = config.getPassword();
        if(username == null || username.isEmpty() || password == null || password.isEmpty()){
            System.out.println("请设置用户名密码用于登录");
            if(exit) {
                System.exit(-1);
            }
            return ;
        }

        String csrfToken = executor.execute("config->csrf_token").orElseThrow();
        int key = Integer.parseInt(executor.execute("encryption->key_id").orElseThrow());
        String publicKey = executor.execute("encryption->public_key").orElseThrow();

        HttpClientContext context = new HttpClientContext();
        Https.connect(client,new HttpGet("https://www.instagram.com"),context);
        HttpPost post = new HttpPost("https://www.instagram.com/accounts/login/ajax/");
        String time = String.valueOf(System.currentTimeMillis()/1000);
        post.addHeader("X-CSRFToken",csrfToken);
        List<NameValuePair> pairs = new ArrayList<>();
        pairs.add(new BasicNameValuePair("username",username));
        pairs.add(new BasicNameValuePair("enc_password","#PWD_INSTAGRAM_BROWSER:10:"+time+":"+encrypt(key,publicKey,password,time)));
        UrlEncodedFormEntity entity = new UrlEncodedFormEntity(pairs);
        post.setEntity(entity);
        String content = Https.toString(client,post,context);
        if(content.contains("\"authenticated\": true")) {
            String sid = context.getCookieStore().getCookies().stream().filter(c->"sessionid".equals(c.getName())).map(Cookie::getValue).findAny().orElseThrow();
            Configure.get().getConfig().setSid(sid).store();
        } else {
            System.out.println("登录失败");
            if(exit) {
                System.exit(-1);
            }
        }
    }

    private interface SetFail{
        void onFail();
    }

    private static String encrypt(int key, String pkey, String password, String time) throws Exception{
        int overheadLength = 48;
        byte[] pkeyArray = new byte[pkey.length() / 2];
        for (int i = 0; i < pkeyArray.length; i++) {
            int index = i * 2;
            int j = Integer.parseInt(pkey.substring(index, index + 2), 16);
            pkeyArray[i] = (byte) j;
        }

        byte [] y = new byte[password.length()+36+16+overheadLength];

        int f = 0;
        y[f] = 1;
        y[f += 1] = (byte)key;
        f += 1;

        KeyGenerator keyGenerator = KeyGenerator.getInstance("AES");
        keyGenerator.init(256);

        // Generate Key
        SecretKey secretKey = keyGenerator.generateKey();
        byte[] IV = new byte[12];

        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        SecretKeySpec keySpec = new SecretKeySpec(secretKey.getEncoded(), "AES");
        GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, IV);
        cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmParameterSpec);
        cipher.updateAAD(time.getBytes());

        byte [] sealed = SealedBoxUtility.crypto_box_seal(secretKey.getEncoded(),pkeyArray);
        byte[] cipherText = cipher.doFinal(password.getBytes());
        y[f] = (byte) (255 & sealed.length);
        y[f + 1] = (byte) (sealed.length >> 8 & 255);
        f += 2;
        for(int j=f;j<f+sealed.length;j++){
            y[j] = sealed[j-f];
        }
        f += 32;
        f += overheadLength;

        byte [] c = Arrays.copyOfRange(cipherText,cipherText.length -16,cipherText.length);
        byte [] h = Arrays.copyOfRange(cipherText,0,cipherText.length - 16);

        for(int j=f;j<f+c.length;j++){
            y[j] = c[j-f];
        }
        f += 16;
        for(int j=f;j<f+h.length;j++){
            y[j] = h[j-f];
        }
        return Base64.getEncoder().encodeToString(y);
    }
}
