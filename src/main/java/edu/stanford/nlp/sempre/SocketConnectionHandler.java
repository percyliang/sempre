package edu.stanford.nlp.sempre;


import com.google.common.base.Joiner;
import com.google.common.base.Strings;
import com.google.common.collect.Lists;
import fig.basic.*;
import jline.console.ConsoleReader;

import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;

import java.io.BufferedReader;
import java.io.InputStreamReader;

import java.lang.reflect.Field;
import java.util.*;
import java.net.ServerSocket;
import java.net.Socket;

import edu.stanford.nlp.sempre.Master.*;


public class SocketConnectionHandler implements Runnable {

	private Socket clientSocket;
	private Session session;
	private Master master;

	public SocketConnectionHandler(Socket clientSocket, Session session, Master master) {
		this.clientSocket = clientSocket;
		this.session = session;
		this.master = master;
	}

  public void run() {
  	try {
  	BufferedReader input = new BufferedReader(
                new InputStreamReader(clientSocket.getInputStream()));
        PrintWriter output = new PrintWriter(clientSocket.getOutputStream(), true);
        try {
          String line;
          while (clientSocket.isConnected()) {
            line = input.readLine();
            if (line != null) {
              LogInfo.logs("%s", line);
              int indent = LogInfo.getIndLevel();
              try {
                Response res = master.processQuery(session, line);
                System.out.println(res.getAll());
                output.println(res.getAll());
              } catch (Throwable t) {
                while (LogInfo.getIndLevel() > indent)
                  LogInfo.end_track();
                t.printStackTrace();
              }
            }
          }
        } catch (IOException e) {
          e.printStackTrace();
          throw new RuntimeException(e);
        }
    }
    catch (Exception e) {
    	e.printStackTrace();
    }
  }



}