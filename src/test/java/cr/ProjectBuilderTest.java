package cr;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

import javax.xml.bind.DatatypeConverter;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import cr.generated.ops.service.RPCClient;
import cr.service.BuilderManager;
import cr.util.JenkinsAdapter;
import cr.util.RepoHandler;

//@RunWith(SpringRunner.class)
//@SpringBootTest
public class ProjectBuilderTest {
	@Autowired
	private RPCClient client;
	@Autowired
	private static String encryption = "Plaintext";

	@Autowired
	BuilderManager bm;

	// @Test
	public void test1() throws InvalidRemoteException, TransportException, IOException, GitAPIException {// Tue Nov 13
																											// 17:54:19
																											// CET 2018

		// root.setLevel(ch.qos.logback.classic.Level.DEBUG);
		// String repoUrl =
		// "https://github.com/fastfoodcoding/SpringBootMongoDbCRUD.git";
		/* clone */
		String repoUrl = "https://username@sourcerepository/path/sourceproject.git";
		String url = "https://sourcerepository/path/sourceproject.git";
		String localRepo = "/home/fefe/git/jgitexample";
		String remoteRepo = "https://username@destinationrepository/path/destinationproject.git";
		String user = "username";
		String passwd = "password";
//		RepoHandler r = new RepoHandler();
//		r.cloneRepo(localRepo, repoUrl, user, passwd);
//		/* clone */
//		r.rebaseRemoteBuffer(localRepo, remoteRepo, user, passwd);

		// StringTokenizer st=new StringTokenizer(url, "//");
		// String protocol=st.nextToken();
		// String address="";
		// String project=null;
		// int i=0;
		// while(st.hasMoreTokens()) {
		// if(i>0)
		// address+="/";
		// project=st.nextToken();
		// address+=project;
		// i++;
		// }
		// project=project.replace(".git", "");
		// String completeUrl=protocol+"//"+user+"@"+address;
		// System.out.println(completeUrl);
		// System.out.println(bm.detectLanguage(bm.cloneProject(url)));

		// ProjectScan qwe1=bdm.checkProjectsToScan();
		// EncryptedMessage p=qwe1.toEncryptedMessage(encryption);
		// System.out.println(qwe1);
		// EncryptedMessage qwe =
		// client.sendAndReceiveDb(bdm.checkProjectsToScan().toEncryptedMessage(encryption).encodeBase64());
		// System.out.println(qwe);
	}

	// @Test
	public void triggerJenkins() throws IOException, InterruptedException {
		// JenkinsServer js = new JenkinsServer(URI.create("http://ip:8080"),

		//// js.
		// Map<String, Job> jobs = js.getJobs();
		//
		// jobs.keySet().forEach(System.out::println);
		//
		// System.out.println("\n\n\n");
		// js.getJob("Java").build(true);
		// System.out.println("ok");

		JenkinsAdapter ja = new JenkinsAdapter("jenkinsuser", "jenkinsapitoken",
				"http://192.168.56.3:8080");
		System.out.println(ja.buildJob("Java"));

		// SystemInfo systemInfo = client.api().systemApi().systemInfo();
		// JobWithDetails job = js.getJob("Java");
		// QueueReference queueRef = job.build(true);
		//
		// System.out.println("Ref:" + queueRef.getQueueItemUrlPart());
	}

	// @Test
	public void connectToJenkins() throws Exception {
		String stringUrl = "http://jenkinsuser:jenkinsapitoken@192.168.56.3:8080";
		try {
			URL url = new URL("http://192.168.56.3:8080/job/Java/api"); // Jenkins URL localhost:8080, job named 'test'
			String user = "admin"; // username
			String pass = "jenkinsapitoken"; // password or API token
			String authStr = user + ":" + pass;
			String encoding = DatatypeConverter.printBase64Binary(authStr.getBytes());

			HttpURLConnection connection = (HttpURLConnection) url.openConnection();
			connection.setRequestMethod("POST");
			connection.setDoOutput(true);
			connection.setDoInput(true);
			connection.setRequestProperty("Authorization", " Basic " + encoding);
			connection.connect();
			System.out.println(connection.getResponseCode() + " " + connection.getResponseMessage());
			InputStream content = connection.getInputStream();
			BufferedReader in = new BufferedReader(new InputStreamReader(content));
			String line;
			while ((line = in.readLine()) != null) {
				System.out.println(line);
			}
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	@Test
	public void Jenkins() {
		JenkinsAdapter jn=new JenkinsAdapter("jenkinsuser", "jenkinsapitoken", "http://192.168.56.3:8080");
		System.out.println(jn.buildJob("Java"));
	}
}
