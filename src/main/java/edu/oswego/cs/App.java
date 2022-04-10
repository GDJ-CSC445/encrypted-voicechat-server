package edu.oswego.cs;

import io.github.cdimascio.dotenv.Dotenv;

/**
 * Hello world!
 *
 */
public class App 
{
    public static void main( String[] args )
    {
        Dotenv env = null;
        try {
            env = Dotenv.load();
        } catch (Exception e) {
            System.out.println("No .env file found.");
            System.exit(1);
        }

        System.out.println(env.get("HOST"));
        System.out.println(env.get("PORT"));

    }
}
