package cr;

import java.io.DataOutputStream;
import java.io.IOException;
import java.io.StringReader;
import java.net.URL;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.bind.DatatypeConverter;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;

import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.httpclient.methods.PostMethod;
import org.apache.http.client.ClientProtocolException;
import org.junit.Test;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import cr.util.JenkinsAdapter;

public class AppTest {
	// @Test
	public void buildTest() {
		JenkinsAdapter ja = new JenkinsAdapter("admin", "1193719c69e935f7cf7f50bd3e5510b33c",
				"http://34.255.70.61:8080");
		System.out.println(ja.buildJob("Java"));
	}

	// @Test
	public void print() {
		System.out.println("\u001b[0mDate: \u001b[1m\u001b[37m2019-01-21T10:56:13.158Z\u001b[39m\u001b[22m\u001b[0m\n"
				+ "\u001b[0mHash: \u001b[1m\u001b[37m3e3ba3c31ae7628c98ad\u001b[39m\u001b[22m\u001b[0m\n"
				+ "\u001b[0mTime: \u001b[1m\u001b[37m47683\u001b[39m\u001b[22mms\u001b[0m\n"
				+ "\u001b[0mchunk {\u001b[1m\u001b[33m0\u001b[39m\u001b[22m} \u001b[1m\u001b[32mruntime.e6c34cc0af100731e6b7.js, runtime.e6c34cc0af100731e6b7.js.map\u001b[39m\u001b[22m (runtime) 1.12 kB \u001b[1m\u001b[33m[entry]\u001b[39m\u001b[22m\u001b[1m\u001b[32m [rendered]\u001b[39m\u001b[22m\u001b[0m\n"
				+ "\u001b[0mchunk {\u001b[1m\u001b[33m1\u001b[39m\u001b[22m} \u001b[1m\u001b[32mpolyfills.14d623046d2911617058.js, polyfills.14d623046d2911617058.js.map\u001b[39m\u001b[22m (polyfills) 64.4 kB \u001b[1m\u001b[33m[initial]\u001b[39m\u001b[22m\u001b[1m\u001b[32m [rendered]\u001b[39m\u001b[22m\u001b[0m\n"
				+ "\u001b[0mchunk {\u001b[1m\u001b[33m2\u001b[39m\u001b[22m} \u001b[1m\u001b[32mmain.1b7509359bf3b0a6a6a1.js, main.1b7509359bf3b0a6a6a1.js.map\u001b[39m\u001b[22m (main) 1.28 MB \u001b[1m\u001b[33m[initial]\u001b[39m\u001b[22m\u001b[1m\u001b[32m [rendered]\u001b[39m\u001b[22m\u001b[0m\n"
				+ "Done in 53.58s.");
	}

	//@Test trasformare stringhe in date e mandare in backlog il build record se si tenta la build. le date sono contenute nella lista offTimeWindowlist
	public void httpClient() throws ClientProtocolException, IOException, SAXException, ParserConfigurationException {
		HttpClient httpclient = new HttpClient();
		GetMethod httpget = new GetMethod("https://analysiscenter.veracode.com");
		PostMethod httppost = new PostMethod("https://analysiscenter.veracode.com/j_security_check");
		try {
			httpclient.executeMethod(httpget);
			Stream.of(httpget.getResponseHeaders()).forEach(e -> System.out.println(e));// Set-Cookie:
			String cookie = httpget.getResponseHeader("Set-Cookie").getValue().replaceAll("Set-Cookie: ", "");
//			System.out.println("\n" + httpget.getStatusLine() + "\n" + cookie + "\n");

			httppost.addParameter("loc", "");
			httppost.addParameter("j_username", "username");
			httppost.addParameter("j_password", "password");
			httppost.addParameter("j_pin", "Tokencode");
			httppost.setRequestHeader("Cookie", cookie);
			httpclient.executeMethod(httppost);
//			System.out.println("\n" + httppost.getStatusLine() + "\n");

//			System.out.println("\n\n\n");
			final Set<String> set = new HashSet<String>();
			Stream.of(httppost.getResponseHeaders()).filter(e -> e.getName() != null)
					.filter(e -> e.getName().equals("Set-Cookie")).forEach(e -> {
						set.add(e.getValue());
//						System.out.println(e.getValue());
					});// Set-Cookie:
//			System.out.println("\n\n\n");
			set.stream().forEach(System.out::println);
			cookie = String.join("; ", set);
			httpget = new GetMethod("https://analysiscenter.veracode.com/auth/afterlogin.do?show=whenactive");
			httpget.setRequestHeader("Cookie", cookie);
			httpclient.executeMethod(httpget);
//			System.out.println("\n" + httpget.getStatusLine() + "\n");
			String body = httpget.getResponseBodyAsString();
//			System.out.println("\n\n\n" + body);

			String regex = "\\d{4}+[-]+\\d{2}+[-]+\\d{2}+[ ]+\\d{2}+[:]+\\d{2}";

			Pattern pattern = Pattern.compile(regex);

			Matcher matcher = pattern.matcher(body);
			List<String> offTimeWindowlist=new LinkedList<String>();
			while (matcher.find()) {
				offTimeWindowlist.add(body.substring(matcher.start(), matcher.end()));
			}
			offTimeWindowlist.forEach(System.out::println);
			// if (Pattern.matches("(<)(l)(i)(>)", body)) {
			// System.out.println("match");
			// // m.group(0) is the entire matched item, not the first group.
			// // etc...
			// }else {
			// System.out.println("no match");
			// }

		} finally {
			httpget.releaseConnection();
			httppost.releaseConnection();
		}
	}


}
