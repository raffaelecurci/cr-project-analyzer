package cr.util;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.bind.DatatypeConverter;

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
