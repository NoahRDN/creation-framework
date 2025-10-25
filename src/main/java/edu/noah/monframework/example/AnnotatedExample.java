package edu.noah.monframework.example;

import edu.noah.monframework.annotation.InvokeMe;

/** Example class with methods annotated with @InvokeMe. */
public class AnnotatedExample {

    @InvokeMe(path = "/hello")
    public void sayHello() {
        System.out.println("Hello from instance method sayHello() (path=/hello)");
    }

    @InvokeMe(path = "/greet")
    public static void staticGreet() {
        System.out.println("Hello from static method staticGreet() (path=/greet)");
    }

    @InvokeMe(path = "/secret")
    private void secret() {
        System.out.println("Invoked private method secret() (path=/secret)");
    }

    public void notAnnotated() {
        System.out.println("This should not be called by the executor");
    }
}
