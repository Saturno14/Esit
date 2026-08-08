import java.util.Scanner;
import src.*;

public class console{
    public static void main(String[] args) {
        while(true){
            String cmd = "";
            Scanner sc = new Scanner(System.in);
            cmd= sc.nextLine();
            if(!cmd.isEmpty()){
                String[] str = cmd.split(" ");
                switch (str[0]) {
                    case "layer":
                        if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1])<world.getDim()){
                            world.ChengePrintGround(Integer.parseInt(str[1]));
                        }
                        System.out.println("Changed print layer to: "+Integer.parseInt(str[1]));
                        break;
                    case "start":
                        world.ChangeSimulationFlag(true);
                        System.out.println("Simulation status: "+world.GetSimulationFlag());
                        try {
                            Thread.sleep(5000);
                        } catch (Exception e) {}
                        world.DoCycle();
                        break;
                    case "stop":
                        world.ChangeSimulationFlag(false);
                        System.out.println("Simulation status: "+world.GetSimulationFlag());
                        break;
                    case "mela":
                        try {
                            if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1])<=world.getDim()){
                                if(Integer.parseInt(str[2])>=0 && Integer.parseInt(str[2])<=world.getDim()){
                                    world.add(Integer.parseInt(str[1]), world.getDim(), Integer.parseInt(str[2]), "M");
                                }
                        }
                        } catch (Exception e) { System.out.println("Errore mela");}
                        break;
                    case "setFood":
                        if(Integer.parseInt(str[2])>0){
                            Entity_manager.Entity_get(Integer.parseInt(str[1].trim())).setFood(Integer.parseInt(str[2].trim()));
                            System.out.println("Entity: "+Entity_manager.Entity_get(Integer.parseInt(str[1].trim()))+" food set to: "+Integer.parseInt(str[2].trim()));
                        }
                        break;
                    
                    case "Get":
                        if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1])<= Entity_manager.Entity_count()){
                            try {
                                entity temp = Entity_manager.Entity_get(Integer.parseInt(str[1]));
                                System.out.println("ID: "+temp.getId());
                                System.out.println("IsAlive: "+temp.isAlive());
                                System.out.println("Healt: "+temp.getHealt());
                                System.out.println("Food: "+temp.getFood());
                                System.out.println("NetRaward: "+temp.getNetreward());
                                System.out.println("Age: "+temp.getAge());
                                System.out.println("Sex: "+temp.getSex());
                                System.out.println("Pos: "+temp.getPos());
                                
                            } catch (Exception e) { System.out.println("Errore get Entity: "+ e.getMessage());}
                        }
                        break;
                        
                    default:
                        System.out.println("Parametro: "+str[0]);
                        break;
                }
            }
        }
    }
}