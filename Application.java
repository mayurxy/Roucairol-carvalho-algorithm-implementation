import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Random;

/**
 * This class implements the application thread of the program
 * 
 * @author div
 *
 */
public class Application {

	static volatile boolean requestedCriticalSection = false;
	static volatile boolean isInCriticalSection = false;
	static volatile boolean hasAllKeys = false;
	static volatile boolean isRequested = false;
	// static volatile boolean[] terminationArray;
	static volatile boolean terminationIsTrue = false;
	static volatile int termination = 0;
	// log file variables
	PrintWriter out;
	String fileName = "node.log";

	// variables for exponential values
	int[] requestDelay;
	int[] cMean;

	public static void main(String[] args) {
		ConfigRead.parseConfig(args[0], Integer.parseInt(args[1]));
		Application app = new Application();
		// this will start my application
		// It will contains everything required for running the program and
		// completing the protocol.
		app.startApplication();
		// after num of reqs hav been completed, termination protocol will start
		// for this node
	}

	public Application() {
		requestDelay = new int[ConfigRead.numOfReq];
		cMean = new int[ConfigRead.numOfReq];
		// create file
		try {
			out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		System.out.println("File Created");
		out.close();

	}

	/**
	 * Implements the roucairol-carvalho protocol for mutual exclusion of
	 * critical section access
	 */
	public void startApplication() {
		// generating random exponential values
		requestDelay = genNum(ConfigRead.numOfReq, ConfigRead.dMean);
		cMean = genNum(ConfigRead.numOfReq, ConfigRead.cMean);
		// code for starting server
		Server s = new Server(ConfigRead.myNodeId, ConfigRead.nodes);
		// we will start by making persistent channels for this node to all the
		// other nodes in the system
		s.makeChannels();
		// after making the channels, we will start the server to setup for
		// accepting incoming messages
		s.start();
		try {
			Thread.sleep(5000);
		} catch (InterruptedException e1) {
			e1.printStackTrace();
		}
		// after making channels and starting the server, the node will try
		// to enter critical section
		for (int i = 0; i < ConfigRead.numOfReq; i++) {
			csEnter(s, cMean[i]);
			csExecute(cMean[i]);
			csExit(s);
			// amount of delay 'd' in making the next request
			try {
				Thread.sleep(requestDelay[i]);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		// stop application
		++termination;
		// generate timestamp for termination
		long time = TimeStamp.returnTime();
		Message m = new Message("This is a request message", MessageType.TerminationMsg,
				new TimeStamp(time, ConfigRead.myNodeId));
		// send termination
		for (int i = 0; i < ConfigRead.numNodes; i++) {
			if (i != ConfigRead.myNodeId) {
				int myNodeId = ConfigRead.myNodeId;
				// host name of other node
				String ip = ConfigRead.nodes.get(i).hostname;
				// port num of other node
				int port = ConfigRead.nodes.get(i).port;
				// start client to send request msg to other node
				s.sendTermination(myNodeId, ip, port, m, i);
			}
		}
		while (true) {
			if (termination == ConfigRead.numNodes) {
				try {
					System.exit(2);
				} catch (Exception e) {

				}
			}
		}
	}

	/**
	 * This method should be blocking until it gets its request satisfied
	 */
	public void csEnter(Server s, int cMean) {
		long time = TimeStamp.returnTime();
		// the message that is being sent
		Message m = new Message("This is a request message", MessageType.RequestMsg,
				new TimeStamp(time, ConfigRead.myNodeId));
		// add the request to its queue
		s.addToQueue(m);
		Application.requestedCriticalSection = true;
		// check for keys
		if (s.checkForKeys()) {// can enter
			System.out.println("node " + ConfigRead.myNodeId + " has all keys");
			Application.hasAllKeys = true;
			Application.isInCriticalSection = true;
			try {
				out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
				out.println();
				out.append(System.currentTimeMillis() + ":" + ConfigRead.myNodeId + "-Start");
				out.println();
			} catch (IOException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}
			out.close();
			return;
		} else {// send request msgs to other processes
			s.makeRequests(m);
		}
		while (!s.checkForKeys()) {
			try {
				Thread.sleep(cMean);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
		Application.hasAllKeys = true;
		Application.isInCriticalSection = true;
		System.out.println("Node " + ConfigRead.myNodeId + " got all keys");
		try {
			out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
			out.println();
			out.append(System.currentTimeMillis() + ":" + ConfigRead.myNodeId + "-Start");
			out.println();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		out.close();
	}

	public void csExecute(int cMean) {
		System.out.println("Node " + ConfigRead.myNodeId + " executing critical section");
		try {
			// sleep for time 'c' while executing critical section
			Thread.sleep(cMean);
		} catch (InterruptedException e) {
			System.out.println("error in sleeping thread in csExecute");
			e.printStackTrace();
		}
	}

	public void csExit(Server s) {
		try {
			out = new PrintWriter(new BufferedWriter(new FileWriter(fileName, true)));
			out.append(System.currentTimeMillis() + ":" + ConfigRead.myNodeId + "-End");
			out.println();
		} catch (IOException e1) {
			// TODO Auto-generated catch block
			e1.printStackTrace();
		}
		out.close();
		Application.isInCriticalSection = false;
		Application.requestedCriticalSection = false;
		System.out.println("Node " + ConfigRead.myNodeId + " is leaving critical section");
		s.removeFromQueue();
	}

	/**
	 * code for generating exponential values
	 * 
	 * @param n
	 *            : num of requests
	 * @param c
	 *            : mean value of exponential curve
	 * @return
	 */
	public static int[] genNum(int n, int c) {

		int i;
		// n=10;
		// c = 10;
		Random rand = new Random();
		Random random1 = new Random();

		int[] randomnumarray = new int[n];
		int sum = (n * c) / 2;
		int myrandnum = 0;
		int upperbound = Long.valueOf(Math.round(sum / n)).intValue();
		// System.out.println("upperbound::" + upperbound);
		int offset = Long.valueOf(Math.round((upperbound / 2))).intValue();
		// System.out.println("offset::" + offset);
		int variation = sum;
		int finalsum = 0;
		for (i = 0; i < n; i++) {
			myrandnum = rand.nextInt(upperbound) + offset;
			if (((finalsum + myrandnum) > sum) || (i == (n - 1))) {
				myrandnum = sum - finalsum;
			}
			finalsum = finalsum + myrandnum;
			randomnumarray[i] = myrandnum;

			if (finalsum == sum) {
				break;
			}
		}
		// System.out.println("array after distributed mean delay:::" +
		// Arrays.toString(randomnumarray));

		int[] array1 = new int[randomnumarray.length + 1];
		for (int j = 0; j < randomnumarray.length; j++) {
			array1[j] = randomnumarray[j];
		}
		array1[randomnumarray.length] = variation;

		// System.out.println("array after variation:::" +
		// Arrays.toString(array1));

		for (int k = array1.length - 1; k > 0; k--) {
			int index = random1.nextInt(k + 1);
			// System.out.println("index::" + index);
			int a = array1[index];
			array1[index] = array1[k];
			array1[k] = a;
		}
		// System.out.println("array after shuffle:::" +
		// Arrays.toString(array1));
		return array1;

	}

}
