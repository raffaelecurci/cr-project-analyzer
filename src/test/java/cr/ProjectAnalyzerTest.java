package cr;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Paths;

import javax.xml.bind.DatatypeConverter;

import org.eclipse.jgit.api.CloneCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.util.FS;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

import cr.generated.ops.service.RPCClient;
import cr.service._BuilderManager;
import cr.util.JenkinsAdapter;
import cr.util.RepoHandler;

//@RunWith(SpringRunner.class)
//@SpringBootTest
public class ProjectAnalyzerTest {
	@Autowired
	private RPCClient client;
	@Autowired
	private static String encryption = "Plaintext";

	@Autowired
	_BuilderManager bm;

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
		// RepoHandler r = new RepoHandler();
		// r.cloneRepo(localRepo, repoUrl, user, passwd);
		// /* clone */
		// r.rebaseRemoteBuffer(localRepo, remoteRepo, user, passwd);

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

	@Test
	public void testCloneSubModules() throws InvalidRemoteException, TransportException, IOException, GitAPIException {
		String address = "domain:8443/scm/rez/basketservice.git";
		String destinationFolder = "/home/fefe/git/jgitexample";
		String repoUsername = "user";
		String repoPasswd = "password";
		String branch = "dev";
		String commit = "8a4faec4ab0ac9e9b67634924773d96b0183fff0";

		String remoteRepo = "https://user@domain:8443/scm/ver/msbuild.git";

		RepoHandler r = new RepoHandler();
		String completeUrl = "https://" + repoUsername + "@" + address;
		r.cloneRepo(destinationFolder, completeUrl, repoUsername, repoPasswd, branch, commit);
		gitInit(destinationFolder);
		r.rebaseRemoteBuffer(destinationFolder, remoteRepo, repoUsername, repoPasswd);
	}

	private void gitInit(String localRepo) throws IllegalStateException, GitAPIException {
		deleteFolder(localRepo + "/.git");
		Git git = Git.init().setDirectory(new File(localRepo)).call();
		git.add().addFilepattern(".").call();
		git.commit().setMessage("automatic-rebase").call();
	}

	private void deleteFolder(String localRepo) {
		File index = new File(localRepo);
		String[] entries = index.list();
		for (String s : entries) {
			File currentFile = new File(index.getPath(), s);
			if (currentFile.isDirectory())
				deleteFolder(currentFile.getPath());
			currentFile.delete();
		}
	}

	// @Test
	public void sshClone() throws InvalidRemoteException, TransportException, GitAPIException {
		String destinationFolder = "/home/fefe/git/jgitexample";
		SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
			@Override
			protected void configure(OpenSshConfig.Host host, Session session) {
				// do nothing
			}

			@Override
			protected JSch createDefaultJSch(FS fs) throws JSchException {
				JSch defaultJSch = super.createDefaultJSch(fs);
				return defaultJSch;
			}
		};
		CloneCommand cloneCommand = Git.cloneRepository();
		cloneCommand.setURI("ssh://git@domain:7999/rez/basketseednode.git");
		cloneCommand.setTransportConfigCallback(new TransportConfigCallback() {
			@Override
			public void configure(Transport transport) {
				SshTransport sshTransport = (SshTransport) transport;
				sshTransport.setSshSessionFactory(sshSessionFactory);
			}
		});

		cloneCommand.setDirectory(Paths.get(destinationFolder).toFile()).call();
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

		JenkinsAdapter ja = new JenkinsAdapter("jenkinsuser", "jenkinsapitoken", "http://192.168.56.3:8080");
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

	// @Test
	public void Jenkins() {
		JenkinsAdapter jn = new JenkinsAdapter("jenkinsuser", "jenkinsapitoken", "http://192.168.56.3:8080");
		System.out.println(jn.buildJob("Java"));
	}

	// @Test
	public void getLastBuildResult() {
		JenkinsAdapter ja = new JenkinsAdapter("admin", "password", "http://192.168.56.3:8080");
		System.out.println(ja.isBuilding("Java"));
		System.out.println(ja.isBuilding("Java", new Long("69")));
	}
}
