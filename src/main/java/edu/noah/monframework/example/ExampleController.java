package edu.noah.monframework.example;

import edu.noah.monframework.annotation.Controller;
import edu.noah.monframework.annotation.InvokeMe;


@Controller
public class ExampleController {

    @InvokeMe(path = "/ctrl/hello")
    public void hello() {
        System.out.println("ExampleController: hello() invoked for /ctrl/hello");
    }

    @InvokeMe(path = "/ctrl/ping")
    public static void ping() {
        System.out.println("ExampleController: static ping() invoked for /ctrl/ping");
    }

    public void helper() {
        System.out.println("ExampleController: helper() called");
    }
}
