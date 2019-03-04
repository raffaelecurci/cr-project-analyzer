package cr.service;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Optional;
import java.util.StringTokenizer;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;

import cr.ProjectAnalyzerApplication;
import cr.generated.ops.service.RPCClient;
import cr.interf.EncryptedMessage;
import cr.shared.BuildRecord;
import cr.shared.JenkinsJob;
import cr.shared.ProjectList;
import cr.shared.ProjectScan;
import cr.shared.SecurityProject;
import cr.shared.SecurityProjectList;
import cr.util.JenkinsAdapter;
import cr.util.RepoHandler;

@RefreshScope
@EnableAsync
@Service
public class _BuilderManager {
	@Autowired
	private RPCClient client;
	private static String encryption=ProjectAnalyzerApplication.class.getAnnotation(cr.annotation.QueueDefinition.class).encryption();
	private static Logger log = LoggerFactory.getLogger(_BuilderManager.class);
	
	@Value("${projects.storage}")
	private String storagePath;
	@Value("${repo.user}")
	private String repoUsername;
	@Value("${repo.passwd}")
	private String repoPasswd;
	
	
	@Async
	public void checkBuildVerifyScan() {
//		check();
	}
	
	private void check() {
		Object o=new Object();
		while(true) {
			EncryptedMessage response = client.sendAndReceiveDb(checkProjectsToScan().toEncryptedMessage(encryption).encodeBase64());
			if(response!=null) {
				Object tmp[][]=addBuildRecord(response);
				scanProjects(tmp);
			}
			synchronized (o) {
				try {
					o.wait(3000);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
	}
	public void scanProjects(Object[][] toScan) {
		// TODO verify veracode availability
		for (int i = 0; i < toScan.length; i++) {
			scanSingleProject(((BuildRecord)toScan[i][0]), ((List<SecurityProject>)toScan[i][1]));
		}
	}
	
	public void scanSingleProject(BuildRecord br,List<SecurityProject> splist) {
		 Optional<SecurityProject> scan = splist.stream().filter(s->s.getId().equals(br.getIdSecurityProject())).filter(s->s.isAvailable()).findFirst();
		if(scan.isPresent()) {
			SecurityProject sp=scan.get();
			br.setStatus("BUILDSTARTING");
			client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64());
			RepoHandler rh=new RepoHandler();
			StringTokenizer st=new StringTokenizer(sp.getUrl(), "//");
			String protocol=st.nextToken();
			String address="";
			String project=null;
			int i=0;
			while(st.hasMoreTokens()) {
				if(i>0)
					address+="/";
				project=st.nextToken();
				address+=project;
				i++;
			}
			project=project.replace(".git", "");
			String completeUrl=protocol+"//"+repoUsername+"@"+address;
			try {
				rh.rebaseRemoteBuffer(br.getStoragefolder(), completeUrl, repoUsername, repoPasswd);
			} catch (IOException | GitAPIException e) {
				e.printStackTrace();
			} 
			System.out.println("marked jenkins for repository "+sp.getUrl()+" as unavailable and pushed project "+br.getStoragefolder()+" to security repository");
			JenkinsJob jj=new JenkinsJob();
			jj.setIdSecurityRepository(br.getIdSecurityProject());
			EncryptedMessage enc=client.sendAndReceiveDb(jj.toEncryptedMessage(encryption).encodeBase64());
			jj=enc.decodeBase64ToObject();
			JenkinsAdapter ja=new JenkinsAdapter(jj.getUser(), jj.getToken(), jj.getUrl());
			ja.buildJob(jj.getJob());
			System.out.println("Jenkins build for "+br.getStoragefolder()+" launched on "+jj.getUrl()+" on job "+jj.getJob());
		}
		else {
			File f=new File(br.getStoragefolder());
			f.delete();
			deleteBuildRecord(br);
		}
	}
	
	private void deleteBuildRecord(BuildRecord br) {
		br.setStatus("DELETE");
		client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64());	
	}

	public String cloneProject(String projectToclone,String branch) {
		String destinationFolder=(storagePath.charAt(storagePath.length()-1)=='/'?storagePath:storagePath+"/")+new SimpleDateFormat("yyyyMMddHHmmss").format(new Date())+"_" ;
		StringTokenizer st=new StringTokenizer(projectToclone, "//");
		String protocol=st.nextToken();
		String address="";
		String project=null;
		int i=0;
		while(st.hasMoreTokens()) {
			if(i>0)
				address+="/";
			project=st.nextToken();
			address+=project;
			i++;
		}
		project=project.replace(".git", "");
		String completeUrl=protocol+"//"+repoUsername+"@"+address;
		RepoHandler r=new RepoHandler();
		r.cloneRepo(destinationFolder+project, completeUrl,branch, repoUsername, repoPasswd);
		return destinationFolder+project;
	}
	
	public String detectLanguage(String clonedRepo) {
		/* detect language start */
		log.info("Going to analyze "+clonedRepo);
		String[] cmd = { "/bin/sh", "-c","cd "+clonedRepo+" && github-linguist | awk '{print $2}'"};
		Process p1=null;
		String hightest=null;
		try {
			p1 = Runtime.getRuntime().exec(cmd);
			InputStream stdin = p1.getInputStream();
			InputStreamReader isr = new InputStreamReader(stdin);
			BufferedReader br = new BufferedReader(isr);
			String line = null;
			int i=0;
			while ( (line = br.readLine()) != null) {
				if(i==0)
					hightest=line;
				log.info("Detecting "+line);
				i++;
			}
			log.info("Hightest language detected percentage is  " + hightest);
			int exitVal=p1.waitFor();
			log.info("github-linguist: " + exitVal);
			
		} catch (InterruptedException | IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		/* detect language stop */
		return hightest;
	}
	
	private Object[][] addBuildRecord(EncryptedMessage projectList) {
		ProjectList pl=projectList.decodeBase64ToObject();
		System.out.println(pl.toString());
		Object result[][]=new Object[pl.getProject().size()][];
		final int i[]=new int[1];
		i[0]=0;
		pl.getProject().forEach(p->{
			BuildRecord br=new BuildRecord();
			br.setDate(new Date());
			br.setStatus("TOBUILD");
			br.setIdRepository(p.getId());
			String clonedProject=cloneProject(p.getUrl(),p.getBranch());
			String detectedLanguage=detectLanguage(clonedProject);
			SecurityProject sp=new SecurityProject();
			sp.setLanguage(detectedLanguage);
			EncryptedMessage spl=client.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64());
			SecurityProjectList splist=spl.decodeBase64ToObject();
			int securityProjectId=splist.getProject().get(0).getId();
			System.out.println("SecurityProjectId: "+securityProjectId);
			Optional<SecurityProject> availableSecurityProject = splist.getProject().stream().filter(k->k.isAvailable()).findFirst();
			if(availableSecurityProject.isPresent())
				securityProjectId=availableSecurityProject.get().getId().intValue();
			System.out.println("SecurityProjectId: "+securityProjectId);
			br.setIdSecurityProject(securityProjectId);
			br.setStoragefolder(clonedProject);
			System.out.println("Requesting:"+br);
			br.setId(((BuildRecord)(client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64())).decodeBase64ToObject()).getId());
			result[i[0]]=new Object[] {br,splist.getProject()};
			i[0]++;
		});
		return result;
	}
	
	private ProjectScan checkProjectsToScan() {
		Date d=new Date();
		return new ProjectScan(d);
	}
}
