package cr.service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.listener.MessageListenerContainer;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

import com.rabbitmq.client.AMQP.Queue.DeclareOk;

import cr.ProjectAnalyzerApplication;
import cr.generated.config.ApplicationConfigReader;
import cr.generated.ops.MessageSender;
import cr.generated.ops.service.RPCClient;
import cr.interf.EncryptedMessage;
import cr.shared.BuildRecord;
import cr.shared.BuildRecordList;
import cr.shared.Commit;
import cr.shared.Operation;
import cr.shared.Project;
import cr.shared.ProjectFinder;
import cr.shared.SecurityProject;
import cr.shared.SecurityProjectList;
import cr.util.RepoHandler;

@RefreshScope
@Component
public class ProjectAnalyzer {
//	@Autowired
//	private DeclareOk buiCounter;
	@Autowired
	private RPCClient client;
	@Autowired
	private RabbitListenerEndpointRegistry listenersreg;
	@Autowired
	private ApplicationConfigReader applicationConfigReader;
	@Autowired
	private MessageSender messageSender;
	@Autowired
	private RabbitTemplate rabbitTemplate;
	private static String encryption = ProjectAnalyzerApplication.class
			.getAnnotation(cr.annotation.QueueDefinition.class).encryption();
	private static Logger log = LoggerFactory.getLogger(ProjectAnalyzer.class);
	
	@Qualifier("backlogLock")
	@Autowired
	private Object backlogLock;
	
	@Value("${projects.storage}")
	private String storagePath;
	@Value("${repo.user}")
	private String repoUsername;
	@Value("${repo.passwd}")
	private String repoPasswd;
//	private boolean countDown = false;
//	private int count;
	
	
	

	public synchronized EncryptedMessage action(EncryptedMessage message) {
		if (message.getPayloadType().equals("cr.shared.Project"))
			return analyze(message);
		if (message.getPayloadType().equals("cr.shared.Operation"))
			return processOperation(message);
		Operation nop = new Operation("NOP");
		return nop.toEncryptedMessage(encryption).encodeBase64();

	}

	private EncryptedMessage processOperation(EncryptedMessage message) {
		Operation r = message.decodeBase64ToObject();
		if (r.getMessage().equals("STOP_DEQUEUE_ANALYSIS")) {
			MessageListenerContainer buiContainer = listenersreg.getListenerContainer("ana");
			if (buiContainer != null) {
				buiContainer.stop();
				Operation op = new Operation("ANA_LISTENER_STOP");
				log.info("ANALYZER DEQUEING STATUS " + (buiContainer.isRunning()?"RUNNING":"NOT RUNNING") );
				return op.toEncryptedMessage(encryption).encodeBase64();
			}
		} else if (r.getMessage().equals("START_DEQUEUE_ANALYSIS")) {
			MessageListenerContainer buiContainer = listenersreg.getListenerContainer("ana");
			if (buiContainer != null) {
				buiContainer.start();
				Operation op = new Operation("ANA_LISTENER_STARTED");
				log.info("ANALYZER DEQUEING STATUS " + (buiContainer.isRunning()?"RUNNING":"NOT RUNNING") );
				return op.toEncryptedMessage(encryption).encodeBase64();
			}
		} else
			System.out.println(r.getMessage());

		return new Operation("NOP").toEncryptedMessage(encryption).encodeBase64();
	}

	public EncryptedMessage analyze(EncryptedMessage message) {
		Project project = message.decodeBase64ToObject();
		if (needScan(project.getLastscan())) {
			System.out.println(project);
			log.info("Starting analysis for " + project.getName() + " from url " + project.getUrl());
			// clone locally the project and detect the language, then update the project
			// record in the database with the detected technology
			String clonedProject = pushProject(project.getUrl(),project.getBranch(),project.getCommit());
			String detectedLanguage = detectLanguage(clonedProject);
			project.setLanguage(detectedLanguage);
			client.sendAndReceiveDb(project.toEncryptedMessage(encryption).encodeBase64());
			// check if the securityRepository is available. If yes push it to builder queue
			// otherwise save it to database for future use
			SecurityProject sp = new SecurityProject();
			sp.setLanguage(detectedLanguage);
			String mobile=getCommit(project.getId_commit()).getMobile();
			
			EncryptedMessage spl = client.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64());
			SecurityProjectList splist = spl.decodeBase64ToObject();
			Optional<SecurityProject> securityProjectScannerCandidate = splist.getProject().stream().filter(s -> s.isAvailable()).filter(s->s.getMobile().equals(mobile)).findAny();
			
			if (securityProjectScannerCandidate.isPresent() /*&& !countDown*/) {// push it to builder
				// mark securityRepository as unavailable
				System.out.println("\n\nproject scanner available\n\n");
				SecurityProject s = securityProjectScannerCandidate.get();
				sp.setId(s.getId());
				sp.setAvailable(false);
				EncryptedMessage reply = client.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64());
				System.out.println(reply.decodeBase64ToObject().toString());
				BuildRecord br = new BuildRecord();
				br.setDate(new Date());
				br.setIdRepository(project.getId());
				br.setIdSecurityProject(s.getId());
				br.setStoragefolder(clonedProject);
				br.setIdCommit(project.getId_commit());
				br.setStatus("BUILDSTARTING");
				System.out.println("detected language:"+detectedLanguage+" changes "+javaChanges(project.getCommit(), clonedProject));
				if(detectedLanguage.equals("Java"))
					br.setChanges(javaChanges(project.getCommit(), clonedProject));
				
				System.out.println("\n\n\nadding build record");
				EncryptedMessage enc=client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64());
				log.info("Receiving "+enc.toString());
				br= enc.decodeBase64ToObject();
				log.info("Added "+br.toString());
				
				//get commit of the build
//				if(detectedLanguage.equals("Java")) {
//					Commit c=new Commit();
//					c.setId(br.getIdCommit());
//					//verificare prossima istruzione che restituisce {"@class":"cr.shared.Operation","operation":"NOP"}
//					System.out.println("\n\n"+c.toEncryptedMessage(encryption).encodeBase64()+"\n\n");
//					c=(Commit)(client.sendAndReceiveDb(c.toEncryptedMessage(encryption).encodeBase64())).decodeBase64ToObject();
//					br.setChanges(javaChanges(c.getHash(), clonedProject));
//					System.out.println("\n\njava optimization\n\n");
//					EncryptedMessage enc1=client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64());
//					log.info("Receiving "+enc1.toString());
//				}
				
				
				messageSender.sendMessage(rabbitTemplate, applicationConfigReader.getBuiExchange(),applicationConfigReader.getBuiRoutingKey(), br.toEncryptedMessage(encryption).encodeBase64());
			} else {// store it in database
				System.out.println("\n\nno project scanner available\n\n");
				System.out.println("Storing "+project.getName()+" into the DB "+project.getId_commit());
				BuildRecord br = new BuildRecord();
				br.setDate(new Date());
				br.setIdCommit(project.getId_commit());
				br.setStatus("TOBUILD");
				br.setIdRepository(project.getId());
				br.setStoragefolder(clonedProject);
				br.setChanges(javaChanges(project.getCommit(), clonedProject));
				System.out.println("\n\n"+br.toString());
				br.setId(((BuildRecord) (client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64())).decodeBase64ToObject()).getId());
				if(detectedLanguage.equals("Java"))
					br.setChanges(javaChanges(project.getCommit(), clonedProject));
				//get commit of the build
//				if(detectedLanguage.equals("Java")) {
//					Commit c=new Commit();
//					c.setId(br.getIdCommit());
//					//verificare prossima istruzione che restituisce {"@class":"cr.shared.Operation","operation":"NOP"}
//					System.out.println("\n\n"+c.toEncryptedMessage(encryption).encodeBase64()+"\n\n");
//					c=(Commit)(client.sendAndReceiveDb(c.toEncryptedMessage(encryption).encodeBase64())).decodeBase64ToObject();
//					br.setChanges(javaChanges(c.getHash(), clonedProject));
//					System.out.println("\n\njava optimization\n\n");
//					EncryptedMessage enc1=client.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64());
//					log.info("Receiving "+enc1.toString());
//				}
				
				/*if (count>0)
					count--;
				System.out.println("CountDown "+count+" "+countDown);*/
			}
		}
		BuildRecord br = new BuildRecord();
		br.setId(-1L);
		br.setIdCommit(-1L);
		br.setStatus("FIND_BACKLOG");
		BuildRecordList builds = (BuildRecordList) (client
				.sendAndReceiveDb(br.toEncryptedMessage(encryption).encodeBase64())).decodeBase64ToObject();
		List<BuildRecord> brl = builds.getBuilds();
		if(brl.size()>0) {
			notifyBackLog();
		}
		/*if (!countDown) {
			SecurityProject sp = new SecurityProject();
			sp.setLanguage("%");
			boolean blockBuilderDequeuing = ((SecurityProjectList) client
					.sendAndReceiveDb(sp.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject())
							.getProject().stream().allMatch(s -> !s.isAvailable());
			if (blockBuilderDequeuing) {
				// block dequeuing activity of builder instance
				Operation nop = new Operation("STOP_DEQUEUE_BUILDS");
				Operation o = client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
				// count messages enqueued in the builder with buiCounter.getMessageCount()
				count = buiCounter.getMessageCount();
				countDown=true;
				System.out.println("\n\n messages ready on rabbit to be built " + countDown + "\n\n");
				nop = new Operation("START_DEQUEUE_BUILDS");
				o = client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
				
			}
		} else if (count == 0) {
			Operation nop = new Operation("STOP_DEQUEUE_ANALYSIS");
			Operation o = client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
			log.info("STOP_DEQUEUE_ANALYSIS");
			nop = new Operation("STOP_DEQUEUE_BUILDS");
			o = client.sendAndReceiveBui(nop.toEncryptedMessage(encryption).encodeBase64()).decodeBase64ToObject();
			log.info("STOP_DEQUEUE_BUILDS");
		}*/
		Operation nop = new Operation("NOP");
		return nop.toEncryptedMessage(encryption).encodeBase64();
	}
	
	private void notifyBackLog() {
//		System.out.println(!listenersreg.getListenerContainer("ana").isRunning() +" backlogLock:"+backlogLock);
//	    if(!listenersreg.getListenerContainer("ana").isRunning()) {
	    	synchronized (backlogLock) {
	    		log.info("going to call notify");
	    		backlogLock.notifyAll();
			}
//	    }
	}

	private boolean needScan(Date d) {// check if the project need to be scanned
		return true;
	}
	private Commit getCommit(Long idCommit) {
		Commit c=new Commit();
		c.setId(idCommit);
		//verificare prossima istruzione che restituisce {"@class":"cr.shared.Operation","operation":"NOP"}
		System.out.println("\n\n"+c.toEncryptedMessage(encryption).encodeBase64()+"\n\n");
		return (Commit)(client.sendAndReceiveDb(c.toEncryptedMessage(encryption).encodeBase64())).decodeBase64ToObject();
	}
	private String javaChanges(String hash,String localRepo) {
		 String s;
	        Process p;
	        String[] cmd = { "/bin/sh", "-c", "cd " + localRepo + " && git show --shortstat --numstat "+hash+" | grep \"src/main\" | grep -v \"src/test\" | awk '{print $3}'" };
	        LinkedList<String> changes=new LinkedList<String>();
	        try {
	            p = Runtime.getRuntime().exec(cmd);
//	            System.out.println("sh -c '"+command+"'");
	            BufferedReader br = new BufferedReader(
	                new InputStreamReader(p.getInputStream()));
	            while ((s = br.readLine()) != null) {
	            	changes.push(s);
	            }
	            p.waitFor();
//	            System.out.println ("exit: " + p.exitValue());
	            p.destroy();
	        } catch (Exception e) {
	        	e.printStackTrace();
	        }
	        Set<String> changeSet= changes.stream()
					.map(i->{
						if(i.substring(0, i.lastIndexOf("src/main")).endsWith("/"))
							return i.substring(0, i.lastIndexOf("src/main")-1);
						else
							return i.substring(0, i.lastIndexOf("src/main"));
						})
					.collect(Collectors.toSet());
	        log.info("Changes in Set: "+changeSet.size());
	        changeSet.forEach(e->log.info(e));
	        String theChange=null;
	        if(changeSet.size()!=0) {
	        	theChange=String.join("|", changeSet);
	        	if(theChange.equals(""))
	        		theChange="*";
	        }
	        else
	        	theChange="*";
	        log.info("Changes detected: "+theChange);
		return theChange;
	}
	private String pushProject(String projectToclone,String branch,String commit) {
		String destinationFolder = (storagePath.charAt(storagePath.length() - 1) == '/' ? storagePath
				: storagePath + "/") + new SimpleDateFormat("yyyyMMddHHmmss").format(new Date()) + "_";
		StringTokenizer st = new StringTokenizer(projectToclone, "//");
		String protocol = st.nextToken();
		String address = "";
		String project = null;
		int i = 0;
		while (st.hasMoreTokens()) {
			if (i > 0)
				address += "/";
			project = st.nextToken();
			address += project;
			i++;
		}
		project = project.replace(".git", "");
		String completeUrl = protocol + "//" + repoUsername + "@" + address;
		RepoHandler r = new RepoHandler();
		r.cloneRepo(destinationFolder + project, completeUrl, repoUsername, repoPasswd,branch, commit);
//		r.cloneRepo(destinationFolder + project, completeUrl, branch, repoUsername, repoPasswd);
		return destinationFolder + project;
	}

	public String detectLanguage(String clonedRepo) {
		/* detect language start */
		log.info("Going to analyze " + clonedRepo);
		String[] cmd = { "/bin/sh", "-c", "cd " + clonedRepo + " && github-linguist | awk '{print $2}'" };
		Process p1 = null;
		String hightest = null;
		try {
			p1 = Runtime.getRuntime().exec(cmd);
			InputStream stdin = p1.getInputStream();
			InputStreamReader isr = new InputStreamReader(stdin);
			BufferedReader br = new BufferedReader(isr);
			String line = null;
			int i = 0;
			while ((line = br.readLine()) != null) {
				if (i == 0)
					hightest = line;
				log.info("Detecting " + line);
				i++;
			}
			log.info("Hightest language detected percentage is  " + hightest);
			int exitVal = p1.waitFor();
			log.info("github-linguist: " + exitVal);

		} catch (InterruptedException | IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		/* detect language stop */
		return hightest;
	}

	private ProjectFinder findProjectByNameAndUrl(Commit c) {
		return new ProjectFinder(c.getProjectname(), c.getUrl());
	}

}
