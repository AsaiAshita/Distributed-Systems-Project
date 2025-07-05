package it.unitn.ds;

import java.io.IOException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.tuple.Pair;

 
public class Main {
  //final static int N_SENDERS = 5;

  public static void main(String[] args) {
    // Create an actor system
    final ActorSystem system = ActorSystem.create("DS_Project");
    
    ArrayList<ActorRef> nodes = new ArrayList<>();

    //initial values we will give to the starting set of nodes
    int [] initial_values = {10,20,30,40,50};

    //create all initial actors
    for(int j = 0; j<5; j++){
      ActorRef node = system.actorOf(
              Actor.props(initial_values[j]),    // actor class
              "node_" + j     // the new actor name (unique within the system)
      );
      nodes.add(node);
    }

    // Create a set of values for the nodes
    Map<Integer, Pair<Integer, String>> valuesNode0 = new HashMap<>();
    valuesNode0.put(10, Pair.of(1, "val10"));
    valuesNode0.put(11, Pair.of(1, "val11"));

    Map<Integer, Pair<Integer, String>> valuesNode1 = new HashMap<>();
    valuesNode1.put(10, Pair.of(2, "val100"));
    valuesNode1.put(21, Pair.of(1, "val21"));

    Map<Integer, Pair<Integer, String>> valuesNode2 = new HashMap<>();
    valuesNode2.put(30, Pair.of(1, "val30"));
    valuesNode2.put(31, Pair.of(1, "val31"));

    Map<Integer, Pair<Integer, String>> valuesNode3 = new HashMap<>();
    valuesNode3.put(30, Pair.of(1, "val40"));
    valuesNode3.put(41, Pair.of(1, "val41"));

    Map<Integer, Pair<Integer, String>> valuesNode4 = new HashMap<>();
    valuesNode4.put(10, Pair.of(1, "val1000"));
    valuesNode4.put(51, Pair.of(1, "val51"));


    ArrayList<Map<Integer, Pair<Integer, String>>> allValues = new ArrayList<>();
    allValues.add(valuesNode0);
    allValues.add(valuesNode1);
    allValues.add(valuesNode2);
    allValues.add(valuesNode3);
    allValues.add(valuesNode4);

    // Update the actors view and values stored
    for (int i = 0; i < nodes.size(); i++) {
        ActorRef node = nodes.get(i);
        node.tell(new Actor.UpdateView(nodes), ActorRef.noSender());
        node.tell(new Actor.SetValues(allValues.get(i)), ActorRef.noSender());
    }

    // Create a client
    ActorRef client0 = system.actorOf(Client.props(0,nodes), "client_0");
    ActorRef client1 = system.actorOf(Client.props(1,nodes), "client_1");

    // Obtain a value from nodes
    client1.tell(new Client.GetMsg(10), client1);
    client1.tell(new Client.GetMsg(30), client1);


    // Modify a given value
    //client0.tell(new Client.UpdateMsg(41,"new_val41"), client0);
	
    //the following is a remnant of the lab files I took inspiration from for the basis of the project
    System.out.println(">>> Press ENTER to exit <<<");
    try {
      System.in.read();
    }
    catch (IOException ioe) {}
    finally {
      system.terminate();
    }
  }
}
