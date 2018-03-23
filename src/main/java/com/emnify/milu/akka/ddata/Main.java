package com.emnify.milu.akka.ddata;

import com.typesafe.config.Config;
import com.typesafe.config.ConfigFactory;

import akka.actor.ActorSystem;

import java.io.IOException;

public class Main {

  public static void main(String[] args) throws IOException {

    Config conf = ConfigFactory.load("application.conf");
    ActorSystem system = ActorSystem.create("ddata", conf);

    system.actorOf(DataActor.props(), "dataActor1");

    System.out.println("Press ENTER to shutdown");
    System.in.read();
    system.terminate();
  }
}
