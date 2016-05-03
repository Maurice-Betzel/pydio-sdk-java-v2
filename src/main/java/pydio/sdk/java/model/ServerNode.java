package pydio.sdk.java.model;



import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.OutputStream;
import java.net.URI;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import pydio.sdk.java.security.CertificateTrust;
import pydio.sdk.java.utils.Pydio;

/**
 * Class that wrap a server properties
 * @author pydio
 *
 */

public class ServerNode implements Node{
		
	private boolean mLegacy = false;
	private String mScheme = null;
	private String mHost = null;
	private String mPath = null;
	private int mPort = 80;
	private boolean mSSLUnverified = false;
	private String mUser;
	private String mLabel = null;
	private String mUrl = null;
	private Map<String, String> mConfigs = null;
	private Properties mProperties = null;
	private byte[] mChallengeData;
	private X509Certificate[] mLastUnverifiedCertificateChain;
	private CertificateTrust.Helper mTrustHelper;
	private String mCaptcha;
	private boolean mRememberPassword;
	private int mLastResponseCode = Pydio.OK;

	

	public void initFromProperties(Properties spec) {}
    @Override
    public void initFromFile(File file) {}
    @Override
    public String getProperty(String key) {
		if(mProperties == null) return null;
		return mProperties.getProperty(key);
	}
	@Override
	public void setProperty(String key, String value) {
		if(mProperties == null){
			mProperties = new Properties();
		}
		mProperties.setProperty(key, value);
	}

	public void initFromXml(org.w3c.dom.Node xml) {}

	public void initFromJson(JSONObject json) {}

	public String path(){
		return mPath;
	}

	public String label() {
		return mLabel;
	}

	public int type() {
		return Node.TYPE_SERVER;
	}




	public CertificateTrust.Helper getTrustHelper(){
		return mTrustHelper;
	}

	public ServerNode init(String url){
		if(!url.endsWith("/")){
			url += "/";
		}
		mUrl = url;
		URI uri = URI.create(url);
		mScheme = uri.getScheme();
		mHost = uri.getHost();
		mPath = uri.getPath();
		mPort = uri.getPort();
		return this;
	}

	public ServerNode init(String url, CertificateTrust.Helper helper){
		this.init(url);
		mTrustHelper = helper;
		return this;
	}

	public ServerNode init(String url, String user, CertificateTrust.Helper helper){
		this.init(url);
		mUser = user;
		mTrustHelper = helper;
		return this;
	}

    public boolean legacy(){
        return mLegacy;
    }
	
	public boolean SSLUnverified(){
		return mSSLUnverified;
	}

	public String address(){
		String path = mScheme + "://" + mHost + path();
		if(!path.endsWith("/"))
			return path + "/";
		return path;
	}

	public String host(){
		return mHost;
	}
	
	public String scheme(){
		return mScheme;
	}

	public int port(){
		return mPort;
	}

	public String url(){
		if(mUrl != null) return mUrl;

        String url = mScheme.toLowerCase()+"://"+ mHost;
        if(mPort > 0 && mPort != 80){
            url += ":"+ mPort;
        }
        return mUrl = url+ mPath;
	}

	public String user(){
		return mUser;
	}

	public String getRemoteConfig(String name){
		if(mConfigs == null) return null;
		return mConfigs.get(name);
	}

    public boolean equals(Object o){
        try{
            return this == o || (o instanceof Node) && ((Node)o).type() == type() && label().equals(((Node)o).label()) && path().equals(((Node)o).path());
        }catch(NullPointerException e){
            return false;
        }
    }

	public int lastResponseCode(){
		return mLastResponseCode;
	}

	public String getAuthenticationChallengeResponse(){
		String c = mCaptcha;
		mCaptcha = null;
		return c;
	}

	public X509Certificate[] certificateChain() {
		return mLastUnverifiedCertificateChain;
	}

	public byte[] getChallengeData(){
		return  mChallengeData;
	}


	public ServerNode addConfig(String key, String value){
		if(mConfigs == null) mConfigs = new HashMap<String, String>();
		mConfigs.put(key, value);
		return this;
	}

	public ServerNode setLastUnverifiedCertificateChain(X509Certificate[] chain){
		mLastUnverifiedCertificateChain = chain;
		return this;
	}

	public ServerNode setLegacy(boolean leg){
		mLegacy = leg;
		return this;
	}

	public ServerNode setUser(String user){
		mUser = user;
		return this;
	}

	public ServerNode setSSLUnverified(boolean v){
		mSSLUnverified = v;
		return this;
	}

	public ServerNode setLabel(String label){
		mLabel = label;
		return this;
	}

	public ServerNode setScheme(String scheme){
		this.mScheme = scheme;
		return this;
	}

	public ServerNode setCertificateTrustHelper(CertificateTrust.Helper helper){
		mTrustHelper = helper;
		return this;
	}

	public ServerNode setLastRequestResponseCode(int code){
		mLastResponseCode = code;
		return this;
	}

	public ServerNode setRememberPassword(boolean rememberPassword){
		mRememberPassword = rememberPassword;
		return this;
	}

	public ServerNode setAuthenticationChallengeResponse(String captchaCode){
		mCaptcha = captchaCode;
		return this;
	}

	public ServerNode setChallengeData(byte[] data){
		mChallengeData = data;
		return this;
	}

}
