package cr.util;

import java.io.File;
import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.LinkedList;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.api.errors.InvalidRemoteException;
import org.eclipse.jgit.api.errors.TransportException;
import org.eclipse.jgit.lib.Constants;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RepoHandler {
	private final Logger root = LoggerFactory.getLogger(getClass());

	public RepoHandler cloneRepo(String localRepo, String repoUrl, String repoUser, String repoPasswd) {
		try {
			root.info("Cloning " + repoUrl + " into " + localRepo);
			Git.cloneRepository()
					.setBranch("refs/heads/master")
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


	public void cloneAndPush(String srcRemoteRepo, String localRepo, String dstRemoteRepo, String dstRemoteUser,
			String dstRemotePasswd, String srcRemoteUser, String srcRemotePasswd)
			throws InvalidRemoteException, TransportException, IOException, GitAPIException {
		cloneRepo(localRepo, srcRemoteRepo, srcRemoteUser, srcRemotePasswd);
		rebaseRemoteBuffer(localRepo, dstRemoteRepo, dstRemoteUser, dstRemotePasswd);
	}


}