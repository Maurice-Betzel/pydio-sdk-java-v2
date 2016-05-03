package pydio.sdk.java.transport;

import org.json.JSONObject;
import org.w3c.dom.Document;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

import pydio.sdk.java.http.ContentBody;
import pydio.sdk.java.http.HttpResponse;
import pydio.sdk.java.model.ServerNode;

/**
 * Created by pydio on 13/02/2015.
 */
public interface Transport {

    public static int MODE_SESSION = 1;
    public static int MODE_RESTFUL = 2;

    public int requestStatus();

    public HttpResponse getResponse(String action, Map<String, String> params) throws IOException;

    public String getStringContent(String action, Map<String, String> params) throws IOException;

    public Document getXmlContent(String action, Map<String, String> params) throws IOException;

    public JSONObject getJsonContent(String action, Map<String, String> params);

    public InputStream getResponseStream(String action, Map<String, String> params) throws IOException;

    public Document putContent( String action, Map<String, String> params, ContentBody contentBody) throws IOException;

    public void setServer(ServerNode server);
}
