package cr.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.merge.MergeStrategy;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.eclipse.jgit.util.FS;
import org.eclipse.jgit.transport.JschConfigSessionFactory;
import org.eclipse.jgit.transport.OpenSshConfig;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;



public class RepoHandler {
	private final Logger root = LoggerFactory.getLogger(getClass());

	public RepoHandler cloneRepo(String localRepo, String repoUrl, String branch,String repoUser, String repoPasswd) {
		try {
			root.info("Cloning " + repoUrl + " into " + localRepo+" from "+branch);
			Git.cloneRepository()
					.setBranch("refs/heads/"+branch)
					.setURI(repoUrl)
					.setCredentialsProvider(new UsernamePasswordCredentialsProvider(repoUser, repoPasswd))
					.setDirectory(Paths.get(localRepo).toFile()).call();
			root.info("Completed Cloning");
		} catch (GitAPIException e) {
			root.info("Exception occurred while cloning repo");
			e.printStackTrace();
		}
		return this;
	}
	public RepoHandler cloneRepo(String localRepo, String repoUrl, String repoUser, String repoPasswd,String branch,String hash) {
		try {
			root.info("Cloning " + repoUrl + " into " + localRepo);
			Git git=Git.cloneRepository()
					.setBranch("refs/heads/"+branch)
					.setURI(repoUrl)
					.setCredentialsProvider(new UsernamePasswordCredentialsProvider(repoUser, repoPasswd))	
					.setDirectory(Paths.get(localRepo).toFile()).call();
			git.checkout().setStartPoint(hash);
			Collection<String> submodule = git.submoduleInit().call();
			System.out.println(submodule.size());
			if(submodule.size()>0) {
				submodule.forEach(s->System.out.println(s));
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
				};;
				git.submoduleUpdate()
					.setStrategy(MergeStrategy.RECURSIVE)
					.setTransportConfigCallback( new TransportConfigCallback() {
						  @Override
						  public void configure( Transport transport ) {
						    SshTransport sshTransport = ( SshTransport )transport;
						    sshTransport.setSshSessionFactory( sshSessionFactory );
						  }
						} )
					.call();
				//controllare in modo ricorsivo se il submodule ne richiama un altro ed eliminare .git/ 
				submodule.forEach(s->{System.out.println(localRepo+"/"+s + "/.git");deleteFolder(localRepo+"/"+s + "/.git");});
			}
			root.info("Completed Cloning of "+hash);
		} catch (GitAPIException e) {
			root.info("Exception occurred while cloning repo");
			e.printStackTrace();
		}
		return this;
	}
	private void deleteFolder(String localRepo) {
		File index = new File(localRepo);
		if(index.isDirectory()) {
			String[] entries = index.list();
			for (String s : entries) {
				File currentFile = new File(index.getPath(), s);
				if (currentFile.isDirectory())
					deleteFolder(currentFile.getPath());
				currentFile.delete();
			}
		}else {
			File parent = new File(localRepo.replace("/.git", ""));
			File f[]=parent.listFiles();
			for (int i = 0; i < f.length; i++) {
				if(f[i].getName().startsWith(".git"))
					f[i].delete();
			}
		}
		
	}
	// return last commit id
	public String rebaseRemoteBuffer(String localRepo, String remoteRepo, String user, String passwd)
			throws IOException, InvalidRemoteException, TransportException, GitAPIException {
		File localDirectory = new File(localRepo);
		Git git = Git.open(localDirectory);
		git.push().setCredentialsProvider(new UsernamePasswordCredentialsProvider(user, passwd)).setRemote(remoteRepo)
				.setForce(true).setPushAll().setPushTags().call();
		Repository repository = git.getRepository();
		ObjectId lastCommitId = repository.resolve(Constants.HEAD);
		String last = lastCommitId.toString();
		root.info(last);
		root.info(last.substring(12, last.length() - 1));
		return last.substring(12, last.length() - 1);
	}


	public void cloneAndPush(String srcRemoteRepo, String branch,String localRepo, String dstRemoteRepo, String dstRemoteUser,
			String dstRemotePasswd, String srcRemoteUser, String srcRemotePasswd)
			throws InvalidRemoteException, TransportException, IOException, GitAPIException {
		cloneRepo(localRepo, srcRemoteRepo,branch, srcRemoteUser, srcRemotePasswd);
		rebaseRemoteBuffer(localRepo, dstRemoteRepo, dstRemoteUser, dstRemotePasswd);
	}


}