import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

public class Main {
    public static void main(String[] args) throws FileNotFoundException {
        String pref = "Link_State_Routing_with_Multithreading/src/tests/";
        String[] paths = {
                pref + "input_1.txt",
                pref + "input_2.txt",
                pref + "input_3.txt",
                pref + "input_4.txt",
                pref + "input_5.txt"};
        for(String path: paths) {
            ExManager m = new ExManager(path);
            m.read_txt();

            int num_of_nodes = m.getNum_of_nodes();

            Scanner scanner = new Scanner(new File(path));
            while(scanner.hasNextLine()){
                String line = scanner.nextLine();
                if(line.contains("start")){
                    m.start();
                    Node n = m.getNode(1 + (int)(Math.random() * num_of_nodes));
                    n.print_graph();
                    System.out.println();
                }

                if(line.contains("update")){
                    String[] data = line.split(" ");
                    m.update_edge(Integer.parseInt(data[1]), Integer.parseInt(data[2]), Double.parseDouble(data[3]));
                }
            }
            m.terminate();
        }
    }
}
