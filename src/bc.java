/* 2018-01-14:
bc.java for BlockChain
Dr. Clark Elliott for CSC435

This is some quick sample code giving a simple framework for coordinating multiple processes in a blockchain group.

INSTRUCTIONS:

Set the numProceses class variable (e.g., 1,2,3), and use a batch file to match

AllStart.bat:

REM for three procesess:
start java bc 0
start java bc 1
java bc 2

You might want to start with just one process to see how it works.

Thanks: http://www.javacodex.com/Concurrency/PriorityBlockingQueue-Example

Notes to CDE:
Optional: send public key as Base64 XML along with a signed string.
Verfy the signature with public key that has been restored.

*/

import java.util.*;
import java.io.*;
import java.net.*;
import java.util.concurrent.*;

// CDE: Would normally keep a process block for each process in the multicast group:
//class ProcessBlock {
//    int processID;
//    PublicKey pubKey;
//    int port;
//    String IPAddress;
//}

// Class to keep track of the ports used by the different servers in the bc application.
class Ports {
    // starting point for port number scheme. Port numbers will be given out based on process ID.
    public static int KeyServerPortBase = 6050;
    public static int UnverifiedBlockServerPortBase = 6051;
    public static int BlockchainServerPortBase = 6052;

    // port to send and receive public keys to and from other processes
    public static int KeyServerPort;
    // port to send and receive unverified blocks
    public static int UnverifiedBlockServerPort;
    // port to send and receive blockchains
    public static int BlockchainServerPort;

    // assigning ports based on arbitrary base ports incremented by process ID x 1000
    public void setPorts() {
        KeyServerPort = KeyServerPortBase + (bc.PID * 1000);
        UnverifiedBlockServerPort = UnverifiedBlockServerPortBase + (bc.PID * 1000);
        BlockchainServerPort = BlockchainServerPortBase + (bc.PID * 1000);
    }
}

// worker thread that will read public keys from other bc processes (including this process)
class PublicKeyWorker extends Thread {
    // socket, passed in as argument. This will be a connection to another bc process
    Socket sock;
    // constructor, setting local reference to socket passed in
    PublicKeyWorker(Socket s) {
        sock = s;
    }

    public void run() {
        try {
            // creating reader to read data from socket (sent from other processes)
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            // reading in data from socket (which will be a public key)
            String data = in.readLine();
            // printing out public key to console
            System.out.println("Got key: " + data);
            // closing connection
            sock.close();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }
}

class PublicKeyServer implements Runnable {
    //public ProcessBlock[] PBlock = new ProcessBlock[3]; // CDE: One block to store info for each process.

    public void run() {
        // setup
        int q_len = 6;
        // socket to receive public keys from other processes (including self)
        Socket sock;
        // printing port used to console
        System.out.println("Starting Key Server input thread using " + Integer.toString(Ports.KeyServerPort));
        try {
            // server socket to accept connections from processes trying to share their public keys
            ServerSocket servsock = new ServerSocket(Ports.KeyServerPort, q_len);
            while (true) {
                // waiting to accept a socket connection (will block here until a connection is received)
                sock = servsock.accept();
                // starting a PublicKeyWorker in a new thread.
                // This frees up the PublicKeyServer to accept new connections from other processes
                new PublicKeyWorker(sock).start();
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }
}

// server to accept unverified blocks from other bc processes, including this process
class UnverifiedBlockServer implements Runnable {
    // thread-safe queue which will be passed in as argument.
    // This is where we will put the unverified blocks we receive.
    BlockingQueue<String> queue;
    // constructor. Setting local reference to the queue which will be passed in.
    UnverifiedBlockServer(BlockingQueue<String> queue){
        this.queue = queue;
    }

    // Worker class that will be spawned in a different thread each time a new unverified block is received.
    // This worker will process the unverified block, freeing up the UnverifiedBlockServer to accept
    // new connections with new blocks
    class UnverifiedBlockWorker extends Thread {
        // Socket connection to another bc process (could be self).
        // This connection will be passed in from the UnverifiedBlockServer.
        // This is how we will receive the unverified block we are about to process.
        Socket sock;

        // local socket reference set in constructor
        UnverifiedBlockWorker(Socket s) {
            sock = s;
        }

        public void run() {
            try {
                // reader to read in unverified block from the socket
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                // reading in data
                String data = in.readLine();
                // printing data to console
                System.out.println("Put in priority queue: " + data + "\n");
                // putting data (unverified block) into queue
                queue.put(data);
                // closing connection
                sock.close();
            } catch (Exception x) {
                x.printStackTrace();
            }
        }
    }

    public void run() {
        // setup
        int q_len = 6;
        // local socket reference
        Socket sock;
        // printing UnverifiedBlockServer port to console
        System.out.println("Starting the Unverified Block Server input thread using " +
                Integer.toString(Ports.UnverifiedBlockServerPort));
        try {
            // creating server socket to accept connections from other bc processes sending unverified blocks
            // (other processes could be self)
            // port number retreived from previously set port numbers in Ports
            ServerSocket servsock = new ServerSocket(Ports.UnverifiedBlockServerPort, q_len);
            while (true) {
                // accepting an incoming connection from a bc process (will block here until connection is received)
                sock = servsock.accept();
                // starting an UnverifiedBlockWorker in a new thread and passing it the socket.
                // This allows the UnverifiedBlockServer to be freed up to accept new connections
                new UnverifiedBlockWorker(sock).start();
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }
}

// Class which does the work of verifying unverified blocks.
// It gets these blocks from the thread-safe queue that it shares with the UnverifiedBlockServer.
class UnverifiedBlockConsumer implements Runnable {
    // thread safe queue, passed in through the constructor
    BlockingQueue<String> queue;
    int PID;

    UnverifiedBlockConsumer(BlockingQueue<String> queue) {
        this.queue = queue;
    }

    public void run() {
        String data;
        PrintStream toServer;
        Socket sock;
        String newblockchain;
        String fakeVerifiedBlock;

        System.out.println("Starting the Unverified Block Priority Queue Consumer thread.\n");
        try {
            while (true) {
                // take an unverified block from the shared queue
                data = queue.take();
                // print block to console
                System.out.println("Consumer got unverified: " + data);

                // Doing fake work
                int j;
                // trying to get the right number an arbitrary number of times (100)
                for (int i = 0; i < 100; i++) {
                    // picking a random number between 0 and 9
                    j = ThreadLocalRandom.current().nextInt(0, 10);
                    // sleeping for 5 seconds to simulate work being done
                    try {
                        Thread.sleep(500);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    // if the random number was less than 3, we pretend that the puzzle has been solved
                    // and the block has been verified. Otherwise we try again with another random number.
                    if (j < 3) break;
                }
                // If we've reached this point it means we have a verified block to add to the blockchain.

                // if statement checks to see if data has already been added to the blockchain.
                // Doesn't always work but we don't care
                if (bc.blockchain.indexOf(data.substring(1, 9)) < 0) {
                    // creating the fake verified block to add to the blockchain from the data we just verified
                    fakeVerifiedBlock = "[" + data + " verified by P" + bc.PID + " at time "
                            + Integer.toString(ThreadLocalRandom.current().nextInt(100, 1000)) + "]\n";
                    // printing out verified block
                    System.out.println(fakeVerifiedBlock);
                    // adding new block to blockchain
                    String tempblockchain = fakeVerifiedBlock + bc.blockchain;
                    // sending out new blockchain to all bc processes, including self
                    for (int i = 0; i < bc.numProcesses; i++) {
                        // creating a socket to connect to each process
                        sock = new Socket(bc.serverName, Ports.BlockchainServerPortBase + (i * 1000));
                        // creating a print stream to write the data to the socket
                        toServer = new PrintStream(sock.getOutputStream());
                        // writing the new blockchain to the socket, sending it to the other processes
                        toServer.println(tempblockchain);
                        // flushing the socket connection to make sure data gets sent immediately
                        toServer.flush();
                        // closing connection
                        sock.close();
                    }
                }
                // pause for 1.5 seconds
                Thread.sleep(1500);
            }
        } catch (Exception e) {
            System.out.println(e);
        }
    }
}

// Worker class to process blockchains received from other bc processes (including self)
class BlockchainWorker extends Thread {
    // Local socket reference. The socket is passed in through the constructor from the BlockchainServer.
    // The socket is a connection to a bc process.
    Socket sock;
    BlockchainWorker(Socket s) {
        sock = s;
    }

    public void run() {
        try {
            // Creating a reader to read in blockchain data from the socket
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String data = "";
            String data2;
            // reading in data from the socket and concatenating it into one string.
            // At the end of reading it will have read in the whole updated blockchain
            while ((data2 = in.readLine()) != null) {
                data = data + data2;
            }
            // setting the process's blockchain variable to this new updated blockchain
            bc.blockchain = data;
            // printing out the new blockchain to the console
            System.out.println("         --NEW BLOCKCHAIN--\n" + bc.blockchain + "\n\n");
            // closing the connection
            sock.close();
        } catch (IOException x) {
            x.printStackTrace();
        }
    }
}

// Server to accept blockchains from other bc processes (including self)
class BlockchainServer implements Runnable {
    public void run() {
        // setup
        int q_len = 6;
        // socket to receive blockchains through
        Socket sock;
        // printing out port number
        System.out.println("Starting the blockchain server input thread using " + Integer.toString(Ports.BlockchainServerPort));
        try {
            // creating server socket to accept connections from other bc processes (including self)
            ServerSocket servsock = new ServerSocket(Ports.BlockchainServerPort, q_len);
            while (true) {
                // accepting connection
                sock = servsock.accept();
                // starting BlockchainWorker in another thread, freeing up BlockchainServer to accept new connections.
                // Passing in socket to worker.
                new BlockchainWorker(sock).start();
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }
}

public class bc {
    static String serverName = "localhost";
    static String blockchain = "[First block]";
    static int numProcesses = 3;
    static int PID = 0;

    // send out public key and unverified blocks to other bc processes
    public void MultiSend (){
        // this socket will be used to connect to servers in other processes
        Socket sock;
        // this print stream will be used to write data to other servers
        PrintStream toServer;

        try{
            // Send out public key to all bc processes' PublicKeyServers (including myself).
            // We can find their port numbers based on the port numbering scheme using their process IDs
            /** changing this later i think */
            for(int i=0; i< numProcesses; i++){
                sock = new Socket(serverName, Ports.KeyServerPortBase + (i * 1000));
                toServer = new PrintStream(sock.getOutputStream());
                toServer.println("FakeKeyProcess" + bc.PID); toServer.flush();
                sock.close();
            }
            /***/
            // pause to make sure all processes received the public keys
            Thread.sleep(1000);
            // send unverified blocks to all processes, including myself
            /** replace this with reading in data and creating unverified blocks to send out */
            String fakeBlockA = "(Block#" + Integer.toString(((bc.PID+1)*10)+4) + " from P"+ bc.PID + ")";
            String fakeBlockB = "(Block#" + Integer.toString(((bc.PID+1)*10)+3) + " from P"+ bc.PID + ")";
            for(int i=0; i< numProcesses; i++){
                sock = new Socket(serverName, Ports.UnverifiedBlockServerPortBase + (i * 1000));
                toServer = new PrintStream(sock.getOutputStream());
                toServer.println(fakeBlockA);
                toServer.flush();
                sock.close();
            }
            for(int i=0; i< numProcesses; i++){
                sock = new Socket(serverName, Ports.UnverifiedBlockServerPortBase + (i * 1000));
                toServer = new PrintStream(sock.getOutputStream());
                toServer.println(fakeBlockB);
                toServer.flush();
                sock.close();
            }
            /***/
        }catch (Exception x) {x.printStackTrace ();}
    }

    public static void main(String args[]){
        int q_len = 6; // setup
        // if argument present, parse it as the process ID. Otherwise, default to zero
        PID = (args.length < 1) ? 0 : Integer.parseInt(args[0]);
        // print user instructions and the process ID for this process
        System.out.println("Clark Elliott's BlockFramework control-c to quit.\n");
        System.out.println("Using processID " + PID + "\n");

        // Create a thread-safe queue to store unverified blocks.
        // The UnverifiedBlockServer will put blocks into this queue and the UnverifiedBlockConsumer will remove
        // blocks from this queue, hence the need for a thread-safe data structure.
        final BlockingQueue<String> queue = new PriorityBlockingQueue<>();
        // setting port numbers for PublicKeyServer, UnverifiedBlockServer, and BlockchainServer
        new Ports().setPorts();

        // starting up PublicKeyServer in a new thread
        new Thread(new PublicKeyServer()).start();
        // starting up UnverifiedBlockServer in a new thread, and passing in the queue created above
        new Thread(new UnverifiedBlockServer(queue)).start();
        // starting up BlockchainServer in a new thread
        new Thread(new BlockchainServer()).start();
        // pause for 5 seconds to make sure other bc processes have started
        try{Thread.sleep(5000);}catch(Exception e){}
        // send out public key, unverified blocks
        new bc().MultiSend();
        // pause for 1 second to make sure all processes received data
        try{Thread.sleep(1000);}catch(Exception e){}

        // start up UnverifiedBlockConsumer in a new thread
        new Thread(new UnverifiedBlockConsumer(queue)).start();
    }
}