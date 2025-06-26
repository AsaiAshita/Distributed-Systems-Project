package it.unitn.ds;
import java.io.IOException;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;

public class Main {
  //final static int N_SENDERS = 5;

  public static void main(String[] args) {
    // Create an actor system
    final ActorSystem system = ActorSystem.create("DS_Project");

    //initial values we will give to the starting set of nodes
    int [] initial_values = {10,20,30,40,50};

    //create all initial actors
    for(int j = 0; j<5; j++){
      final ActorRef receiver = system.actorOf(
              Actor.props(initial_values[j]),    // actor class
              "node_" + j     // the new actor name (unique within the system)
      );
    }
	
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
