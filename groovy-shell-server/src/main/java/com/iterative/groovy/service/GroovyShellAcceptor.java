/**
 * Copyright 2011 Denis Bazhenov
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.iterative.groovy.service;

import groovy.lang.Binding;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

import static java.lang.Thread.currentThread;
import static java.lang.Thread.yield;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * @author Denis Bazhenov
 */
public class GroovyShellAcceptor implements Runnable {

	private static final Logger log = getLogger(GroovyShellAcceptor.class);

	private final ServerSocket serverSocket;
	private final Binding binding;
	private Set<ClientTaskThread> clientThreads = Collections
			.newSetFromMap(new WeakHashMap<ClientTaskThread, Boolean>());
	private List<String> defaultScripts;

	private static int clientSequence = 0;

	public GroovyShellAcceptor(int port, Binding binding, List<String> defaultScripts) throws IOException {
		if (port <= 0) {
			throw new IllegalArgumentException("Port number should be positive integer");
		}
		serverSocket = new ServerSocket(port);
		serverSocket.setSoTimeout(1000);
		this.binding = binding;
		this.defaultScripts = defaultScripts;
	}

	@Override
	public void run() {
		try {
			log.info("Groovy shell started on {}", serverSocket);

			while (!currentThread().isInterrupted()) {
				try {
					Socket clientSocket = serverSocket.accept();
					log.debug("Groovy shell client accepted: {}", clientSocket);

					synchronized (this) {
						ClientTask clientTask = new ClientTask(clientSocket, binding, defaultScripts);
						ClientTaskThread clientThread = new ClientTaskThread(clientTask, "GroovyShClient-"
								+ clientSequence++);
						clientThreads.add(clientThread);

						clientThread.start();

						log.debug("Groovy shell thread started: {}", clientThread.getName());
					}
				} catch (SocketTimeoutException e) {
					// Didn't receive a client connection within the SoTimeout
					// interval ... continue with another
					// accept call if the thread wasn't interrupted
					yield();
				} catch (SocketException e) {
					log.error("Stopping groovy shell thread", e);
					break;
				}
			}

		} catch (Exception e) {
			log.error("Error in shell dispatcher thread. Stopping thread", e);

		} finally {
			killAllClients();
			closeQuietly(serverSocket);
			log.info("Groovy shell stopped");
		}
	}

	public synchronized void killAllClients() {
		for (ClientTaskThread thread : clientThreads) {
			thread.kill();
		}
		clientThreads.clear();
	}

	private static void closeQuietly(ServerSocket socket) {
		try {
			socket.close();
		} catch (IOException e) {
			log.warn("Error while closing socket", e);
		}
	}
}