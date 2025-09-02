package it.unitn.ds;

import java.io.IOException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

import java.util.*;
import java.util.concurrent.TimeUnit;

import org.apache.commons.lang3.tuple.Pair;



public class Main {

  public static Map<ActorRef, Integer> sortByValue(Map<ActorRef, Integer> map) {
    // Convert map entries to a list
    List<Map.Entry<ActorRef, Integer>> entryList = new ArrayList<>(map.entrySet());

    // Sort the list by value
    Collections.sort(entryList, (entry1, entry2) -> entry1.getValue().compareTo(entry2.getValue()));

    // Create a new LinkedHashMap to keep the sorted order
    Map<ActorRef, Integer> sortedMap = new LinkedHashMap<>();
    for (Map.Entry<ActorRef, Integer> entry : entryList) {
      sortedMap.put((entry.getKey()), entry.getValue());
    }

    return sortedMap;
  }

  public static void main(String[] args) throws InterruptedException {
    // Create an actor system
    final ActorSystem system = ActorSystem.create("DS_Project");
    
    ArrayList<ActorRef> nodes = new ArrayList<>();

    Map<ActorRef, Integer> id_ref_association = new HashMap<>();

    Config.init();

    //the following variable decides how many nodes the system starts with.
    //Change it in order to modify execution if you choose to modify N
    int number_of_initial_nodes = 5;

    if(number_of_initial_nodes < Config.N){
        System.out.println("Error: N is greater than the number of nodes in the system");
        System.exit(1);
    }

    //create all initial actors
    for(int j = 0; j<number_of_initial_nodes; j++){
      ActorRef node = system.actorOf(
              Actor.props(10*(j+1)),    // actor class
              "node_" + j     // the new actor name (unique within the system)
      );
      nodes.add(node);
      id_ref_association.put(node, (10*(j+1)));
    }

    id_ref_association = sortByValue(id_ref_association);


    // Create a set of values for the nodes. Here we assume N=3.
    // If number_of_initial_nodes is changed, please modify the code below
    // to fit for the change.
    ArrayList<Integer> values_to_assign = new ArrayList<>();
    ArrayList<Map<Integer, Pair<Integer, String>>> allValues = new ArrayList<>();
    for(int i=0; i<number_of_initial_nodes; i++){
        Map<Integer, Pair<Integer, String>> valuesNode = new HashMap<>();
        allValues.add(valuesNode); //we create a map of values for each node
    }
    for(int i=0; i<number_of_initial_nodes; i++){
        int position = i;
        values_to_assign.add((i*10)+4); //4, 14, 24...
        for(int j=0; j<Config.N; j++){
            allValues.get(position%number_of_initial_nodes).put((i*10)+4, Pair.of(1, "val" + ((i*10)+4)));
            position = position + 1;
        }
        Config.MOST_RECENT_VERSION.put((i*10)+4, 1);
        position = i;
        values_to_assign.add(((i+1)*10)-2); //8, 18, 28...
        for(int j=0; j<Config.N; j++){
            allValues.get(position%number_of_initial_nodes).put(((i+1)*10)-2, Pair.of(1, "val" + (((i+1)*10)-2)));
            position = position + 1;
        }
        Config.MOST_RECENT_VERSION.put(((i+1)*10)-2, 1);
    }

    /*
    System.out.println(values_to_assign);
    System.out.println(Config.MOST_RECENT_VERSION);
    for(int i=0; i<number_of_initial_nodes; i++){
        System.out.println(id_ref_association.get(nodes.get(i)));
        System.out.println(allValues.get(i));
    }
    */

    // Create clients.
    ArrayList<ActorRef> clients = new ArrayList<>();
    ActorRef client0 = system.actorOf(Client.props(0,nodes), "client_0");
    ActorRef client1 = system.actorOf(Client.props(1,nodes), "client_1");
    ActorRef client2 = system.actorOf(Client.props(2,nodes), "client_2");
    ActorRef client3 = system.actorOf(Client.props(3,nodes), "client_3");
    clients.add(client0);
    clients.add(client1);
    clients.add(client2);
    clients.add(client3);

    // Update the actors view and values stored
    for (int i = 0; i < nodes.size(); i++) {
        ActorRef node = nodes.get(i);
        node.tell(new Actor.UpdateView(nodes), ActorRef.noSender());
        node.tell(new Actor.SetValues(allValues.get(i)), ActorRef.noSender());
        node.tell(new Actor.SetIdAssociation(id_ref_association), ActorRef.noSender());
        node.tell(new Actor.SetClientsView(clients), ActorRef.noSender());
    }

    for(int i=0; i<clients.size(); i++){
      Config.FIFO.put(clients.get(i), new ArrayList<>());
      Config.VECTOR_CLOCK.put(clients.get(i), 0);
    }

    /*nodes.get(1).tell(new Actor.CrashMsg(), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(7);
    ActorRef node = system.actorOf(
            Actor.props(19),    // actor class
            "node_19"     // the new actor name (unique within the system)
    );

    node.tell(new Actor.JoinMsg(19, nodes.get(4)), ActorRef.noSender());

    TimeUnit.SECONDS.sleep(4);

    node = system.actorOf(
            Actor.props(23),    // actor class
            "node_23"     // the new actor name (unique within the system)
    );

    node.tell(new Actor.JoinMsg(23, nodes.get(4)), ActorRef.noSender());

    TimeUnit.SECONDS.sleep(4);

    client0.tell(new Client.GetMsg(45), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.UpdateMsg(4, "Antananarivo"), client2);

    TimeUnit.SECONDS.sleep(4);

    nodes.get(1).tell(new Actor.RecoveryMsg(nodes.get(0)), ActorRef.noSender());

    TimeUnit.SECONDS.sleep(7);

    node = system.actorOf(
            Actor.props(234),    // actor class
            "node_234"     // the new actor name (unique within the system)
    );

    node.tell(new Actor.JoinMsg(234, nodes.get(4)), ActorRef.noSender());

    TimeUnit.SECONDS.sleep(7);

    client0.tell(new Client.GetMsg(45), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.UpdateMsg(4, "Antananarivo"), client2);
    client3.tell(new Client.GetMsg(11), client3);
     */

    //first execution, mix of write and read operation on every single element, no conflicts
    //second execution, multiple reads onto the same key
    //third execution, Read/Write conflicts onto the same key
    //fourth execution Write/Write conflicts onto the same key
    //join of a new node
    //some operations
    //crash of a node
    //resume operations
    //recovery
    //operations
    //multiple crashes
    //operations
    int number_of_values = values_to_assign.size();

    //Simulate an execution of some update and get operations
    System.out.println("=== STARTING SIMULATION ===");
    System.out.println("-- Simulate an execution of some update and get operations --");
    client0.tell(new Client.GetMsg(4), client0);
    client1.tell(new Client.UpdateMsg(4, "FirstUpdate"), client1);
    TimeUnit.SECONDS.sleep(3);
    client2.tell(new Client.GetMsg(4), client2);
    TimeUnit.SECONDS.sleep(3);
    System.out.println("-- Simulation to test for FIFO-ness and sequential consistency --");
    //Simulation to test for FIFO-ness and sequential consistency
    client2.tell(new Client.UpdateMsg(4, "UpdateA"), client2);
    TimeUnit.SECONDS.sleep(3);
    client2.tell(new Client.UpdateMsg(4, "UpdateB"), client2);
    TimeUnit.SECONDS.sleep(3);
    client2.tell(new Client.UpdateMsg(4, "UpdateC"), client2);
    TimeUnit.SECONDS.sleep(3);
    client2.tell(new Client.GetMsg(4), client2);
    TimeUnit.SECONDS.sleep(3);
    System.out.println("-- Crash and Recovery simulation --");
    // Crash and Recovery simulation
    nodes.get(1).tell(new Actor.CrashMsg(), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    nodes.get(1).tell(new Actor.RecoveryMsg(nodes.get(0)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    System.out.println("-- Simulation to test the Join --");
    //Simulation to test the Join
    ActorRef node = system.actorOf(
              Actor.props(99),
              "node_99"
      );
    node.tell(new Actor.JoinMsg(99, nodes.get(0)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(5);
    client3.tell(new Client.UpdateMsg(14, "NewValAfterJoin"), client3);
    client0.tell(new Client.GetMsg(14), client0);
    TimeUnit.SECONDS.sleep(3);
    System.out.println("-- Simulation to test the Leave --");
    // Simulate leave operation
    client0.tell(new Client.GetMsg(4), client0);
    TimeUnit.SECONDS.sleep(3);
    nodes.get(3).tell(new Actor.LeaveMsg(3, nodes.get(3)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(3);
    client0.tell(new Client.GetMsg(4), client0);
    TimeUnit.SECONDS.sleep(3);
    System.out.println("-- Final consistency check --");
    // Final consistency check
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.GetMsg(44), client2);
    client3.tell(new Client.GetMsg(24), client3);
    TimeUnit.SECONDS.sleep(3);

    System.out.println("\n=== SIMULATION COMPLETED ===");

      /*

    client0.tell(new Client.GetMsg(4), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.UpdateMsg(4, "Vallelunga"), client2);
    client2.tell(new Client.UpdateMsg(4, "Vallegioia"), client2);
    client2.tell(new Client.UpdateMsg(4, "Vallarsa"), client2);
    client2.tell(new Client.GetMsg(4), client2);
    client3.tell(new Client.GetMsg(4), client3);
    client2.tell(new Client.GetMsg(4), client2);

    TimeUnit.SECONDS.sleep(5);

    client0.tell(new Client.GetMsg(4), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.UpdateMsg(4, "Venezuela"), client2);
    client3.tell(new Client.UpdateMsg(24, "Paraguay"), client3);
    client0.tell(new Client.UpdateMsg(14, "Nicaragua"), client0);
    client1.tell(new Client.UpdateMsg(4, "Antananarivo"), client1);
    client3.tell(new Client.GetMsg(4), client3);
    client2.tell(new Client.GetMsg(4), client2);

    TimeUnit.SECONDS.sleep(2);
    client3.tell(new Client.GetMsg(4), client3);
    client2.tell(new Client.GetMsg(24), client2);
    client3.tell(new Client.GetMsg(4), client3);
    client2.tell(new Client.GetMsg(14), client2);

    TimeUnit.SECONDS.sleep(3);
    client2.tell(new Client.GetMsg(4), client2);
    client2.tell(new Client.GetMsg(24), client2);
    client2.tell(new Client.GetMsg(28), client2);
    client2.tell(new Client.GetMsg(38), client2);

    client2.tell(new Client.UpdateMsg(4, "Swatziland"), client2);
    client2.tell(new Client.UpdateMsg(24, "Danimarca"), client2);
    client2.tell(new Client.UpdateMsg(28, "Botswana"), client2);
    client2.tell(new Client.UpdateMsg(38, "Isole Salomone"), client2);

    client2.tell(new Client.GetMsg(4), client2);
    client2.tell(new Client.GetMsg(24), client2);
    client2.tell(new Client.GetMsg(28), client2);
    client2.tell(new Client.GetMsg(38), client2);
    */
    //Simulation to test the Join
    /*
    ActorRef node = system.actorOf(
            Actor.props(23),    // actor class
            "node_23"     // the new actor name (unique within the system)
    );

    node.tell(new Actor.JoinMsg(23, nodes.get(2)), ActorRef.noSender());

    TimeUnit.SECONDS.sleep(10);

    nodes.add(node);
    node = system.actorOf(
            Actor.props(48),    // actor class
            "node_48"     // the new actor name (unique within the system)
    );

    node.tell(new Actor.JoinMsg(48, nodes.get(0)), ActorRef.noSender());
    */
    // Simulate leave operation
    /*
    client0.tell(new Client.GetMsg(4), client0);
    TimeUnit.SECONDS.sleep(3);
    nodes.get(1).tell(new Actor.LeaveMsg(1, nodes.get(1)), ActorRef.noSender());
    TimeUnit.SECONDS.sleep(3);
    client0.tell(new Client.GetMsg(4), client0);
    TimeUnit.SECONDS.sleep(3);
     */


    //Simulate an execution of some update and get operations.
    //They were written randomly, so they may not be the best when testing the system.
    //However, for the time being, it illustrates whether the system works or not.

    /*
    //simulation to test for FIFO-ness and (possibly) sequential consistency
    client0.tell(new Client.GetMsg(4), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.UpdateMsg(4, "Antananarivo"), client2);
    client3.tell(new Client.GetMsg(4), client3);
    client0.tell(new Client.GetMsg(4), client0);
    client2.tell(new Client.GetMsg(4), client2);
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.UpdateMsg(4, "Vilnius"), client2);
    client3.tell(new Client.GetMsg(4), client3);
    client0.tell(new Client.GetMsg(4), client0);
    client0.tell(new Client.GetMsg(4), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client3.tell(new Client.GetMsg(4), client3);
    client2.tell(new Client.UpdateMsg(4, "Riga"), client2);
    client0.tell(new Client.GetMsg(4), client0);
    TimeUnit.SECONDS.sleep(4);
    client0.tell(new Client.GetMsg(4), client0);
    client1.tell(new Client.GetMsg(4), client1);
    client2.tell(new Client.GetMsg(4), client2);
    client3.tell(new Client.GetMsg(4), client3);
    */

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
