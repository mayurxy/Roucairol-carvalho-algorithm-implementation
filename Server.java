import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.concurrent.ConcurrentHashMap;

/**
 * This class implements the system thread of the program.
 * 
 * @author div
 *
 */
public class Server extends Thread {

	int myNodeId;
	ArrayList<Node> nodes;
	ServerSocket s;
	// queue to store request messages received
	ArrayList<Message> queue;
	// public HashMap<Integer, Message> que;
	public ConcurrentHashMap<Integer, Message> que;
	int counter = 1;

	// hashmap to store socket connection and object output stream
	HashMap<Integer, Socket> socketConnection;
	HashMap<Integer, ObjectOutputStream> oosConnection;
	static PrintWriter out;
	String logfilename = "node.log";

	public Server(int myNodeId, ArrayList<Node> nodes) {
		this.myNodeId = myNodeId;
		this.nodes = nodes;
		socketConnection = new HashMap<>();
		oosConnection = new HashMap<>();
		queue = new ArrayList<>();
		que = new ConcurrentHashMap<>();
	}

	@Override
	public void run() {
		try {
			ObjectInputStream oInput = null;

			out = new PrintWriter(new BufferedWriter(new FileWriter(logfilename, true)));

			while (true) {
				if (counter < nodes.size()) {
					Thread.sleep(300);
					Socket clientSocket = s.accept();
					InputStream input = clientSocket.getInputStream();
					oInput = new ObjectInputStream(input);
					++counter;
				}
				Message m = (Message) oInput.readObject();
				System.out.println("\n\n\n\n\n\n\n");
				System.out.println("I have received:" + m.s + " with the following details");
				System.out.println("Timestamp:" + m.ts.time);
				System.out.println("Sending Node:" + m.ts.nodeId);
				System.out.println("Receiving Node:" + myNodeId);
				System.out.println("Message type:" + m.messageType);
				System.out.println("This node is in critical section? " + Application.isInCriticalSection);
				System.out.println("This node has requested for cs? " + Application.requestedCriticalSection);
				System.out.println("\n\n\n\n\n\n");
				// when we a request message from a node, then we will check if
				// we have the key for that node. If we do then we send a reply
				// message,else we ignore the request
				out.println("this is file");
				out.close();
				if (m.messageType == MessageType.RequestMsg) {
					if (Application.isInCriticalSection) {
						// System.out.println("node being added to hashmap
						// of
						// node: " + myNodeId + " is : " + m.ts.nodeId);
						// synchronized (que) {
						//
						// }
						que.put(m.ts.nodeId, m);
						continue;
					} else if (Application.requestedCriticalSection) {
						// System.out.println("This node " + myNodeId + "
						// has
						// requested for critical section");
						// compare timestamps
						// if my request is smaller, do nothing
						// else if my request is larger, then
						// sendReplyandRequestMsg
						if (que.get(myNodeId).ts.time > m.ts.time) {
							// sendReplyandRequestmsg
							sendReplyAndRequest(m.ts.nodeId);
						} else {
							// System.out.println(
							// "node being added to hashmap of node: " +
							// myNodeId + " is : " + m.ts.nodeId);
							// synchronized (que) {
							// que.put(m.ts.nodeId, m);
							// }
							que.put(m.ts.nodeId, m);
						}
					} else {
						// sendReplyMsg
						sendReply(m.ts.nodeId);
						synchronized (nodes) {
							nodes.get(myNodeId).csKey.put(m.ts.nodeId, false);
							System.out.println("setting key as false for " + myNodeId + " for key with " + m.ts.nodeId);
						}

					}
				} else if (m.messageType == MessageType.ReplyandRequestMsg) {
					// adding key to our set and adding request to our queue
					synchronized (nodes) {
						nodes.get(myNodeId).csKey.put(m.ts.nodeId, true);
					}
					// nodes.get(m.ts.nodeId).csKey.put(myNodeId, false);
					// System.out.println("node being added to hashmap of node:
					// " + myNodeId + " is : " + m.ts.nodeId);
					// synchronized (que) {
					// que.put(m.ts.nodeId, m);
					// }
					que.put(m.ts.nodeId, m);

				} else if (m.messageType == MessageType.ReplyMsg) {
					// adding key to our set
					synchronized (nodes) {
						nodes.get(myNodeId).csKey.put(m.ts.nodeId, true);
					}
					// nodes.get(m.ts.nodeId).csKey.put(myNodeId, false);

				} else if (m.messageType == MessageType.TerminationMsg) {

				} else {
					System.out.println("Congratulations!! you have received nobel prize.");
				}
			}
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (ClassNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	/**
	 * Sending request message to processes to get keys to enter critical
	 * section
	 * 
	 * @param m
	 *            : Request Message
	 */
	public void makeRequests(Message m) {
		// System.out.println("Calling make request for node:" + myNodeId);
		// sending request only to those nodes who's keys we dont have
		for (int i : socketConnection.keySet()) {
			System.out.println("Does node " + myNodeId + " has keys?" + nodes.get(myNodeId).csKey.get(i));
			if (nodes.get(myNodeId).csKey.get(i) == false) {
				try {
					System.out.println("Node " + myNodeId + " is making request for key from" + i);
					oosConnection.get(i).writeObject(m);
					// Thread.sleep(1000);
				} catch (IOException e) {
					System.out.println("Error in sending request messages");
					e.printStackTrace();
				}
				// } catch (InterruptedException e) {
				// e.printStackTrace();
				// }
			}
		}
	}

	/**
	 * Sending keys to requesting node and also sending a request msg along with
	 * the key.
	 * 
	 * Be cautious of timestamp class
	 * 
	 * @param nodeId
	 */
	public void sendReplyAndRequest(int nodeId) {
		// System.out.println("Sending reply and request message from node:" +
		// myNodeId + " to node:" + nodeId);
		Message m = new Message("This is a Reply and Request message", MessageType.ReplyandRequestMsg,
				new TimeStamp(TimeStamp.returnTime(), myNodeId));
		// now that we are giving keys, so remove key from our list
		synchronized (nodes) {
			nodes.get(myNodeId).csKey.put(nodeId, false);
		}
		try {
			// System.out.println("Node " + myNodeId + " is sending reply and
			// request for key to" + nodeId);
			oosConnection.get(nodeId).writeObject(m);
			// Thread.sleep(1000);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// } catch (InterruptedException e) {
		// // TODO Auto-generated catch block
		// e.printStackTrace();
		// }
	}

	/**
	 * Be cautious of calling timestamp class
	 */
	public void sendReply(int nodeId) {
		// System.out.println("Sending reply from node:" + myNodeId + " to
		// node:" + nodeId);
		// now that we are giving keys, so remove key from our list

		Message m = new Message("This is a reply message", MessageType.ReplyMsg,
				new TimeStamp(TimeStamp.returnTime(), myNodeId));
		try {
			// System.out.println("Sending reply message from" + myNodeId + " to
			// node " + nodeId);
			oosConnection.get(nodeId).writeObject(m);
			// Thread.sleep(1000);
		} catch (IOException e) {
			e.printStackTrace();
		}
		// } catch (InterruptedException e) {
		// e.printStackTrace();
		// }
	}

	/**
	 * This class will make persistent connections of this node with all the
	 * other nodes in the system
	 */
	public void makeChannels() {
		try {
			s = new ServerSocket(nodes.get(myNodeId).port);
			Thread.sleep(2000);
			// loop over to make channels
			for (int i = 0; i < nodes.size(); i++) {
				if (i != myNodeId) {
					String host;
					int portNum;
					// getting value from global parameters
					host = nodes.get(i).hostname;
					portNum = nodes.get(i).port;
					// checking log
					// System.out.println(
					// "Channels for node " + i + " has hostname:" + host + "and
					// the port number is:" + portNum);
					InetAddress inet = InetAddress.getByName(host);
					System.out.println("Inet address is: " + inet);
					Socket socket = new Socket(inet, portNum);
					socketConnection.put(i, socket);
					// establishing outputstream for each socket connection
					ObjectOutputStream oos = new ObjectOutputStream(socket.getOutputStream());
					oosConnection.put(i, oos);
					// sleeping to avoid connection refused
					Thread.sleep(5000);

				}
			}
		} catch (IOException e) {
			System.out.println("Error in making server socket");
			e.printStackTrace();
		} catch (InterruptedException e) {
			System.out.println("Error in thread sleep");
			e.printStackTrace();
		}
	}

	/**
	 * This method will check the nodes.csKey to see if this process has all the
	 * keys needed to enter critical section
	 */
	public synchronized boolean checkForKeys() {
		synchronized (nodes) {
			for (int i : nodes.get(myNodeId).csKey.keySet()) {
				if (nodes.get(myNodeId).csKey.get(i) == false)
					return false;
			}
		}
		return true;
	}

	/**
	 * This method adds its request to its own queue. This is done explicitly to
	 * avoid a scenario when it has all the keys but other requesting node has
	 * smaller timestamp
	 * 
	 * @param time
	 */
	public void addToQueue(Message m) {
		// adding message to the queue
		queue.add(m);
		// key will be the nodeID and message will be its value
		// System.out.println("node being added to hashmap of " + myNodeId + "
		// :: " + m.ts.nodeId);
		que.put(m.ts.nodeId, m);
	}

	/**
	 * Removing messages from queue during csExit
	 */
	public void removeFromQueue() {
		// System.out.println("Removing nodes from queue");
		// synchronized (que) {
		try {
			for (int i : que.keySet()) {
				// System.out.println("iterating que");
				if (i != myNodeId) {
					System.out.println("sending reply from node:" + myNodeId + " to node:" + i);
					sendReply(i);
				}
				que.remove(i);
				// que.clear();
			}
		} catch (ConcurrentModificationException e) {
			System.out.println("Concurrent modification");
		}
		// }

	}

	public void sendTermination(int myNodeId, String ip, int port, Message m, int otherNode) {
		try {
			// System.out.println("Node " + myNodeId + " is sending reply and
			// request for key to" + nodeId);
			oosConnection.get(otherNode).writeObject(m);
			// Thread.sleep(1000);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
