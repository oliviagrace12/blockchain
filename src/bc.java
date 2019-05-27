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
class ProcessBlock {
    int processID;
    PublicKey pubKey;
    int port;
    String IPAddress;
}

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

class PublicKeyWorker extends Thread {
    Socket sock;

    PublicKeyWorker(Socket s) {
        sock = s;
    }

    public void run() {
        try {
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String data = in.readLine();
            System.out.println("Got key: " + data);
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
                // accepting a socket connection
                sock = servsock.accept();
                new PublicKeyWorker(sock).start();
            }
        } catch (IOException ioe) {
            System.out.println(ioe);
        }
    }
}

class UnverifiedBlockServer implements Runnable {
    BlockingQueue<String> queue;
    UnverifiedBlockServer(BlockingQueue<String> queue){
        this.queue = queue;
    }

    class UnverifiedBlockWorker extends Thread {
        Socket sock;
        UnverifiedBlockWorker (Socket s) {sock = s;}
        public void run(){
            try{
                BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
                String data = in.readLine ();
                System.out.println("Put in priority queue: " + data + "\n");
                queue.put(data);
                sock.close();
            } catch (Exception x){x.printStackTrace();}
        }
    }

    public void run(){
        int q_len = 6; /* CDE: Number of requests for OpSys to queue */
        Socket sock;
        System.out.println("Starting the Unverified Block Server input thread using " +
                Integer.toString(Ports.UnverifiedBlockServerPort));
        try{
            ServerSocket servsock = new ServerSocket(Ports.UnverifiedBlockServerPort, q_len);
            while (true) {
                sock = servsock.accept();
                new UnverifiedBlockWorker(sock).start();
            }
        }catch (IOException ioe) {System.out.println(ioe);}
    }
}

class UnverifiedBlockConsumer implements Runnable {
    BlockingQueue<String> queue;
    int PID;
    UnverifiedBlockConsumer(BlockingQueue<String> queue){
        this.queue = queue;
    }

    public void run(){
        String data;
        PrintStream toServer;
        Socket sock;
        String newblockchain;
        String fakeVerifiedBlock;

        System.out.println("Starting the Unverified Block Priority Queue Consumer thread.\n");
        try{
            while(true){
                data = queue.take();
                System.out.println("Consumer got unverified: " + data);

                int j;
                for(int i=0; i< 100; i++){
                    j = ThreadLocalRandom.current().nextInt(0,10);
                    try{Thread.sleep(500);}catch(Exception e){e.printStackTrace();}
                    if (j < 3) break;
                }

                if(bc.blockchain.indexOf(data.substring(1, 9)) < 0){
                    fakeVerifiedBlock = "[" + data + " verified by P" + bc.PID + " at time "
                            + Integer.toString(ThreadLocalRandom.current().nextInt(100,1000)) + "]\n";
                    System.out.println(fakeVerifiedBlock);
                    String tempblockchain = fakeVerifiedBlock + bc.blockchain;
                    for(int i=0; i < bc.numProcesses; i++){
                        sock = new Socket(bc.serverName, Ports.BlockchainServerPortBase + (i * 1000));
                        toServer = new PrintStream(sock.getOutputStream());
                        toServer.println(tempblockchain); toServer.flush();
                        sock.close();
                    }
                }
                Thread.sleep(1500);
            }
        }catch (Exception e) {System.out.println(e);}
    }
}

class BlockchainWorker extends Thread {
    Socket sock;
    BlockchainWorker (Socket s) {sock = s;}
    public void run(){
        try{
            BufferedReader in = new BufferedReader(new InputStreamReader(sock.getInputStream()));
            String data = "";
            String data2;
            while((data2 = in.readLine()) != null){
                data = data + data2;
            }
            bc.blockchain = data;
            System.out.println("         --NEW BLOCKCHAIN--\n" + bc.blockchain + "\n\n");
            sock.close();
        } catch (IOException x){x.printStackTrace();}
    }
}

class BlockchainServer implements Runnable {
    public void run(){
        int q_len = 6; /* CDE: Number of requests for OpSys to queue */
        Socket sock;
        System.out.println("Starting the blockchain server input thread using " + Integer.toString(Ports.BlockchainServerPort));
        try{
            ServerSocket servsock = new ServerSocket(Ports.BlockchainServerPort, q_len);
            while (true) {
                sock = servsock.accept();
                new BlockchainWorker (sock).start();
            }
        }catch (IOException ioe) {System.out.println(ioe);}
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