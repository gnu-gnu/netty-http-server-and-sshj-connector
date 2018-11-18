package com.gnu.ssh;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;
import java.util.Optional;

import org.apache.commons.io.IOUtils;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import net.schmizz.sshj.SSHClient;
import net.schmizz.sshj.connection.ConnectionException;
import net.schmizz.sshj.connection.channel.direct.Session;
import net.schmizz.sshj.connection.channel.direct.Session.Command;
import net.schmizz.sshj.transport.TransportException;
import net.schmizz.sshj.userauth.UserAuthException;
import net.schmizz.sshj.userauth.keyprovider.PKCS8KeyFile;

public class Client {
	private PKCS8KeyFile keyFile;
	private SSHClient client;
	private Session session;
	
	public Client() {
		Security.addProvider(new BouncyCastleProvider());
		this.client = new SSHClient();
		this.client.addHostKeyVerifier((host, portnum, key) -> true);
	}

	public SSHClient connect(String hostname, int port, String userId) {
		try {
			client.connect(hostname, port);
			client.authPublickey(userId, keyFile);
			return client;
		} catch (UserAuthException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}
	
	private Command command(String cmd) {
		try {
			session = client.startSession();
			Command command = session.exec(cmd);
			return command;
		} catch (ConnectionException e) {
			e.printStackTrace();
		} catch (TransportException e) {
			e.printStackTrace();
		}
		return null;
	}

	public List<String> getCommandResultAsLines(String cmd) {
		try {
			List<String> lines = IOUtils.readLines(this.command(cmd).getInputStream(), StandardCharsets.UTF_8);
			session.close();
			return lines;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return null;
	}

	public PKCS8KeyFile getKeyFile() {
		return keyFile;
	}

	public void setKeyFile(PKCS8KeyFile keyFile) {
		this.keyFile = keyFile;
	}

	public void setKeyFile(String keyFilePath) {
		PKCS8KeyFile file = new PKCS8KeyFile();
		file.init(new File(Optional.of(Client.class.getClassLoader().getResource(keyFilePath).getPath()).orElse(keyFilePath)));
		this.setKeyFile(file);
	}
	
	public void close() {
		try {
			this.client.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
