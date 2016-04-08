package pydio.sdk.java.transport;

import org.apache.http.Header;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.auth.UsernamePasswordCredentials;
import org.json.JSONObject;
import org.w3c.dom.Document;
import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringReader;
import java.net.URI;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.text.ParseException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import javax.net.ssl.SSLException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import pydio.sdk.java.http.CustomEntity;
import pydio.sdk.java.http.HttpContentBody;
import pydio.sdk.java.http.HttpResponseParser;
import pydio.sdk.java.http.PydioHttpClient;
import pydio.sdk.java.http.PydioHttpClient2;
import pydio.sdk.java.model.ServerNode;
import pydio.sdk.java.security.Crypto;
import pydio.sdk.java.utils.AuthenticationHelper;
import pydio.sdk.java.utils.Log;
import pydio.sdk.java.utils.Pydio;
import pydio.sdk.java.utils.ServerResolution;

/**
 * This class handle a session with a pydio server
 * @author pydio
 *
 */
public class SessionTransport implements Transport{

    public String mIndex = "index.php?";
    public String mSecureToken = null;

    private ByteArrayOutputStream mCaptchaBytes;
    private int mCaptchaBytesLength;

    int mLastRequestStatus = Pydio.OK;
    boolean mAttemptedLogin, mAccessRefused, mLoggedIn = false;
    AuthenticationHelper mHelper;

    PydioHttpClient mHttpClient;

    String mSeed;
    private ServerNode mServerNode;
    private String mAction;
    private boolean mRefreshingToken;

    public SessionTransport(ServerNode server){
        this.mServerNode= server;
        mCaptchaBytes = null;
    }

    public SessionTransport(){}

    private URI getActionURI(String action){
        ServerResolution.resolve(mServerNode);
        String url = mServerNode.url();
        if(action != null && action.startsWith(Pydio.ACTION_CONF_PREFIX)){
            url += action;
        }else{
            url += mIndex;
            if(action != null && !"".equals(action)){
                url += Pydio.PARAM_GET_ACTION+"="+action;
            }
        }
        try{
            return new URI(url);
        }catch(Exception e){
            return null;
        }
    }

    public void login() throws IOException {
        if(mSeed == null){
            getSeed();
        }

        String[] c = mHelper.getCredentials();
        UsernamePasswordCredentials credentials = new UsernamePasswordCredentials(c[0], c[1]);
        String user = credentials.getUserName();
        String password = credentials.getPassword();

        if(!mSeed.trim().equals("-1")){
            try {
                password = Crypto.hexHash(Crypto.HASH_MD5, (Crypto.hexHash(Crypto.HASH_MD5, password.getBytes()) + mSeed).getBytes());
            } catch (NoSuchAlgorithmException e) {
                e.printStackTrace();
            }
        }

        Map<String, String> loginPass = new HashMap<>();
        String captcha_code = mHelper.getChallengeResponse();

        if(captcha_code != null && !"".equals(captcha_code)) {
            loginPass.put(Pydio.PARAM_CAPTCHA_CODE, captcha_code);
        }

        loginPass.put("userid", user);
        loginPass.put("login_seed", mSeed);
        loginPass.put(Pydio.PARAM_SECURE_TOKEN, mSecureToken);
        loginPass.put("password", "*****");
        Log.info("PYDIO SDK : " + "[action=" + Pydio.ACTION_LOGIN + Log.paramString(loginPass) + "]");
        loginPass.put("password", password);

        mCaptchaBytes = null;
        mSeed = "";

        Document doc = HttpResponseParser.getXML(request(getActionURI(Pydio.ACTION_LOGIN), loginPass, null));
        if(doc != null) {
            if (doc.getElementsByTagName("logging_result").getLength() > 0) {
                String result = doc.getElementsByTagName("logging_result").item(0).getAttributes().getNamedItem("value").getNodeValue();
                if (mLoggedIn = result.equals("1")) {
                    Log.info("PYDIO SDK : " + "[LOGIN OK]");
                    mLastRequestStatus = Pydio.OK;
                    String newToken = doc.getElementsByTagName("logging_result").item(0).getAttributes().getNamedItem(Pydio.PARAM_SECURE_TOKEN).getNodeValue();
                    mSecureToken = newToken;

                } else {
                    mLastRequestStatus = Pydio.ERROR_AUTHENTICATION;
                    if (result.equals("-4")) {
                        Log.info("PYDIO SDK : " + "[ERROR CAPCHA REQUESTED]");
                        mLastRequestStatus = Pydio.ERROR_AUTHENTICATION_WITH_CAPTCHA;
                        loadCaptcha();
                    } else {
                        Log.info("PYDIO SDK : " + "[LOGIN FAILED : " + result + "]");
                    }
                    throw  new IOException();
                }
            } else {
                mLastRequestStatus = Pydio.ERROR_OTHER;
            }
        }
    }

    private void refreshSecureToken() throws IOException {
        mRefreshingToken = true;
        Log.info("PYDIO SDK : " + "[action=" + Pydio.ACTION_GET_TOKEN + "]");
        try {
            HttpResponse resp = request(this.getActionURI(Pydio.ACTION_GET_TOKEN), null, null);
            mSecureToken = "";
            JSONObject jObject = new JSONObject(HttpResponseParser.getString(resp));
            mLoggedIn = true;
            mSecureToken = jObject.getString(Pydio.PARAM_SECURE_TOKEN.toUpperCase());
        } catch (ParseException e) {
            e.printStackTrace();
        }
        mRefreshingToken = false;
    }

    private void getSeed() throws IOException{
        Log.info("PYDIO SDK : " + "[action=" + Pydio.ACTION_GET_SEED + "]");
        HttpResponse resp = request(getActionURI(Pydio.ACTION_GET_SEED), null, null);
        mSeed = HttpResponseParser.getString(resp);

        if("-1".equals(mSeed)){
            return;
        }

        String seed = mSeed;
        mSeed = null;

        boolean seemsToBePydio = false;
        Header[] headers = resp.getHeaders("Content-Type");

        for(int i = 0; i < headers.length; i++){
            Header h = headers[i];
            seemsToBePydio |= (h.getValue().toLowerCase().contains("text/plain"));
            seemsToBePydio |= (h.getValue().toLowerCase().contains("text/xml"));
            seemsToBePydio |= (h.getValue().toLowerCase().contains("text/json"));
            seemsToBePydio |= (h.getValue().toLowerCase().contains("application/json"));
        }

        if(seed == null || !seemsToBePydio){
            mLastRequestStatus = Pydio.ERROR_NOT_A_SERVER;
            throw new IOException();
        }

        seed = seed.trim();

        if(seed.contains("\"seed\":-1")) {
            mSeed = "-1";
        }

        if(seed.contains("\"captcha\": true") || seed.contains("\"captcha\":true")){
            loadCaptcha();
            mLastRequestStatus = Pydio.ERROR_AUTHENTICATION_WITH_CAPTCHA;
            throw new IOException();
        }
    }

    private void loadCaptcha() throws IOException {
        boolean image = false;
        Log.info("PYDIO SDK : " + "[action=" + Pydio.ACTION_CAPTCHA + "]");
        HttpResponse resp = getResponse(Pydio.ACTION_CAPTCHA, null);
        Header[] heads = resp.getHeaders("Content-type");
        for (int i = 0; i < heads.length; i++) {
            if (heads[i].getValue().contains("image/png")) {
                image = true;
                break;
            }
        }
        if (image){
            HttpEntity entity = resp.getEntity();
            if(entity != null){
                byte[] buffer = new byte[Pydio.LOCAL_CONFIG_BUFFER_SIZE_DEFAULT_VALUE];
                int read;
                mCaptchaBytesLength = 0;
                InputStream in = entity.getContent();
                mCaptchaBytes = new ByteArrayOutputStream();
                while((read = in.read(buffer, 0, buffer.length)) != -1){
                    mCaptchaBytes.write(buffer, 0, read);
                    mCaptchaBytesLength += read;
                }
                in.close();
            }
        }
    }

    public ByteArrayOutputStream getCaptcha() {
        return mCaptchaBytes;
    }

    private boolean isAuthenticationRequested(HttpResponse response) {
        HttpEntity ent = response.getEntity();

        final boolean[] is_required = {false};
        try {
            CustomEntity entity = new CustomEntity(ent);
            response.setEntity(entity);
            CustomEntity.ContentStream stream = (CustomEntity.ContentStream) entity.getContent();
            byte[] buffer = new byte[1024];
            int read = stream.safeRead(buffer);
            if(read == - 1) return false;
            String xmlString = new String(Arrays.copyOfRange(buffer, 0, read), "utf-8");
            SAXParserFactory factory = SAXParserFactory.newInstance();
            SAXParser parser = factory.newSAXParser();

            DefaultHandler dh = new DefaultHandler() {
                public boolean tag_repo = false, tag_auth = false, tag_msg = false, tag_auth_message = false;
                public String content;
                String xPath;

                public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {
                    if(tag_repo){
                        if(qName.equals(xPath)){
                            is_required[0] = false;
                            throw new SAXException("AUTH");
                        }
                        return;
                    }else if (tag_auth){
                        if(qName.equals("message")){
                            is_required[0] = true;
                            throw new SAXException("AUTH");
                        }
                        return;
                    }else if (tag_msg){
                        return;
                    }

                    boolean registryPart = qName.equals("ajxp_registry_part") && attributes.getValue("xPath") != null;
                    if(tag_repo = registryPart){
                        String attr = attributes.getValue("xPath");
                        if(attr != null){
                            String[] splits = attr.split("/");
                            xPath = splits[splits.length - 1];
                        }
                    }

                    tag_auth = qName.equals("require_auth");
                    tag_msg = qName.equals("message");
                }

                public void endElement(String uri, String localName, String qName) throws SAXException {
                    if(tag_repo && xPath != null || (tag_auth && qName.equals("require_auth"))){
                        is_required[0] = true;//SessionTransport.this.auth_step = "LOG-USER";
                        throw new SAXException("AUTH");
                    }
                }

                public void characters(char ch[], int start, int length) throws SAXException {
                    String str = new String(ch);
                    if (tag_msg){
                        if(str.toLowerCase().contains("you are not allowed to access")){
                            mAccessRefused = is_required[0] = true; //SessionTransport.this.auth_step = "RENEW-TOKEN";
                            throw new SAXException("TOKEN");
                        }
                    }
                }

                public void endDocument() throws SAXException {
                }
            };
            parser.parse(new InputSource(new StringReader(xmlString)), dh);

        } catch (IOException e) {
        } catch (ParserConfigurationException e) {
            e.printStackTrace();
        } catch (SAXException e) {
            String m = e.getMessage();
            if("auth".equalsIgnoreCase(m)){
                mLastRequestStatus = Pydio.ERROR_AUTHENTICATION;
            }else if ("token".equalsIgnoreCase(m)){
                mLastRequestStatus = Pydio.ERROR_OLD_AUTHENTICATION_TOKEN;
            }
        }catch (Exception e){
            e.printStackTrace();
        }

        if(!is_required[0]){
            mLastRequestStatus = Pydio.OK;
        }
        return is_required[0];

    }

    private HttpResponse request(URI uri, Map<String, String> params, HttpContentBody contentBody) throws IOException {

        if(mHttpClient == null){
            mHttpClient = new PydioHttpClient();
            if(mServerNode.trustSSL()){
                mHttpClient.turnSecure();
            }
        }

        if(params == null){
            params = new HashMap<String, String>();
        }

        if(mSecureToken == null && !mRefreshingToken) {
            refreshSecureToken();
        }

        params.put(Pydio.PARAM_SECURE_TOKEN, mSecureToken);

        HttpResponse response;
        for(;;){
            try {
                response = mHttpClient.execute(uri, params, contentBody);
            } catch (IOException e){
                e.printStackTrace();
                if(e instanceof SSLException){

                    if(mServerNode.trustSSL()) {
                        mLastRequestStatus = Pydio.ERROR_UNVERIFIED_CERTIFICATE;
                        throw e;
                    }

                    System.out.println("Cannot verify the server certificate. Turning into custom SSL connection");
                    mHttpClient.destroy();

                    mServerNode.trustSSL(true);
                    mHttpClient = new PydioHttpClient();
                    mHttpClient.turnSecure();

                    continue;
                }

                mLastRequestStatus = Pydio.ERROR_CON_FAILED;
                throw e;
            }catch (Exception e){
                e.printStackTrace();
                if(e instanceof IllegalArgumentException && e.getMessage().toLowerCase().contains("unreachable")){
                    mLastRequestStatus = Pydio.ERROR_UNREACHABLE_HOST;
                }
                throw new IOException();
            }


            if(Arrays.asList(Pydio.no_auth_required_actions).contains(mAction)) return response;


            if(!isAuthenticationRequested(response)){
                boolean isNotAuthAction = Arrays.asList(Pydio.no_auth_required_actions).contains(mAction);
                if(! isNotAuthAction && mLastRequestStatus != Pydio.OK) {
                    mLastRequestStatus = Pydio.OK;
                }
                return response;

            }else{
                mHttpClient.discardResponse(response);
                try {
                    if(mLoggedIn && mAccessRefused){
                        mLastRequestStatus = Pydio.ERROR_ACCESS_REFUSED;
                        throw new IOException("access refused");
                    }

                    if (mLastRequestStatus == Pydio.ERROR_OLD_AUTHENTICATION_TOKEN) {
                        Log.info("PYDIO SDK : " + "[ERROR INVALID TOKEN = " + mSecureToken + "]");
                        refreshSecureToken();
                        if("".equals(mSecureToken)){
                            throw new IOException("authentication required");
                        }
                        params.put(Pydio.PARAM_SECURE_TOKEN, mSecureToken);
                        mAttemptedLogin = true;
                        continue;
                    }

                    if (mLastRequestStatus == Pydio.ERROR_AUTHENTICATION) {
                        Log.info("PYDIO SDK : " + "[ERROR AUTH REQUESTED]");
                        /*if(mSecureToken != null && !"".equals(mSecureToken)) {
                            mHttpClient.clearCookies();
                        }*/
                        getSeed();
                        login();
                        params.put(Pydio.PARAM_SECURE_TOKEN, mSecureToken);
                        mLastRequestStatus = Pydio.OK;
                        mAttemptedLogin = true;
                        continue;
                    }

                    mLastRequestStatus = Pydio.ERROR_OTHER;
                    break;
                }catch (Exception e){
                    return null;
                }
            }
        }
        return response;
    }
    @Override
    public HttpResponse getResponse(String action, Map<String, String> params) throws IOException {
        mAccessRefused = false;
        mLoggedIn = false;
        mAttemptedLogin = false;
        mAction = action;
        mLastRequestStatus = Pydio.OK;
        return request(getActionURI(action), params, null);
    }
    @Override
    public String getStringContent(String action, Map<String, String> params) throws IOException {
        mAccessRefused = false;
        mLoggedIn = false;
        mAttemptedLogin = false;
        mAction = action;
        mLastRequestStatus = Pydio.OK;
        HttpResponse response  = this.request(this.getActionURI(action), params, null);
        return HttpResponseParser.getString(response);
    }
    @Override
    public Document getXmlContent(String action, Map<String, String> params) throws IOException {
        mAccessRefused = false;
        mLoggedIn = false;
        mAttemptedLogin = false;
        mAction = action;
        mLastRequestStatus = Pydio.OK;
        HttpResponse response  = this.request(this.getActionURI(action), params, null);
        return HttpResponseParser.getXML(response);
    }
    @Override
    public JSONObject getJsonContent(String action, Map<String, String> params) {
        mAccessRefused = false;
        mLoggedIn = false;
        mAttemptedLogin = false;
        mAction = action;
        mLastRequestStatus = Pydio.OK;
        return null;
    }
    @Override
    public InputStream getResponseStream(String action, Map<String, String> params) throws IOException {
        mAccessRefused = false;
        mLoggedIn = false;
        mAttemptedLogin = false;
        mAction = action;
        mLastRequestStatus = Pydio.OK;
        return request(getActionURI(action), params, null).getEntity().getContent();
    }
    @Override
    public Document putContent( String action, Map<String, String> params, HttpContentBody contentBody) throws IOException {
        mAccessRefused = false;
        mLoggedIn = false;
        mAttemptedLogin = false;
        mAction = action;
        mLastRequestStatus = Pydio.OK;
        HttpResponse response = request(getActionURI(action), params, contentBody);
        return HttpResponseParser.getXML(response);
    }
    @Override
    public void setServer(ServerNode server){
        this.mServerNode = server;
    }
    @Override
    public int requestStatus(){
        return mLastRequestStatus;
    }
    @Override
    public void setAuthenticationHelper(AuthenticationHelper helper) {
        this.mHelper = helper;
    }
}