package cr.util;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

import javax.xml.bind.DatatypeConverter;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class JenkinsAdapter {
	static private Logger log = LoggerFactory.getLogger(JenkinsAdapter.class);
	private String user;
	private String token;
	private String url;

	public JenkinsAdapter(String user, String token, String url) {
		this.user = user;
		this.token = token;
		this.url = url;
	}
	
	private JSONObject getJsonResponse(HttpURLConnection connection) {
		try {
			connection.connect();
			InputStream is=connection.getInputStream();
			BufferedReader br=new BufferedReader(new InputStreamReader(is));
			StringBuilder sb=new StringBuilder();
			br.lines().forEach(l->sb.append(l+"\n"));
			JSONObject json=new JSONObject(sb.toString());
			return json;
//			return connection.getResponseCode()+" "+connection.getResponseMessage();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public Boolean isBuilding(String JobName,Long buildId) {
		HttpURLConnection connection = prepairHttpRequest("job/"+JobName+"/"+buildId.toString()+"/api/json","GET");
		JSONObject json=getJsonResponse(connection);
		return json.getBoolean("building");		
	}
	public Boolean isBuilding(String JobName) {
		HttpURLConnection connection = prepairHttpRequest("job/"+JobName+"/lastBuild/api/json","GET");
		JSONObject json=getJsonResponse(connection);
		return json.getBoolean("building");
	}
	
	public String buildJob(String JobName) {
		HttpURLConnection connection = prepairHttpRequest("job/"+JobName+"/build","POST");
		try {
			connection.connect();
			return connection.getResponseCode()+" "+connection.getResponseMessage();
		} catch (IOException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	private HttpURLConnection prepairHttpRequest(String path,String method) {
		HttpURLConnection connection=null;
		try {
			URL url = new URL(this.url + "/" + path); 
			String authStr = user + ":" + token;
			String encoding = DatatypeConverter.printBase64Binary(authStr.getBytes());
			connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod(method);
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setRequestProperty("Authorization", " Basic " + encoding);
		} catch (Exception e) {
			e.printStackTrace();
		}
		return connection;
	}

}
