package com.gnu;

import java.util.List;

import com.gnu.server.RestServer;
import com.gnu.ssh.Client;

public class AppMain {
	public static void ssh() {
		Client client = new Client();
		client.setKeyFile("private.key");
		client.connect("localhost", 9122, "vagrant");
		List<String> lines = client.getCommandResultAsLines("ps -ef");
		for(String line : lines) {
			System.out.println(line);
		}
		client.close();
	}
	
	public static void main(String[] args) {
		RestServer server = new RestServer(8787);
		server.start();
	}
}
