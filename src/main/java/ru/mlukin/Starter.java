package ru.mlukin;

public class Starter {
    public static void main(String[] args) {
        if (args.length != 2) {
            System.out.println("Usage: java -jar <jar> <sourceDir> <targetDir>");
            System.exit(1);
        }
        Replicator wsoReplicator = new WSOReplicator(args[0], args[1]);
        wsoReplicator.start();
    }
}
