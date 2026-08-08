import src.*;

public class console{
    public static String input(String input){
        String out = "";
        String cmd = input;
        if(!cmd.isEmpty()){
            String[] str = cmd.split(" ");
            switch (str[0]) {
                case "layer":
                    if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1])<world.getDim()){
                        world.ChengePrintGround(Integer.parseInt(str[1]));
                    }
                    out = ("Changed print layer to: "+Integer.parseInt(str[1]));
                    break;
                case "start":
                    world.ChangeSimulationFlag(true);
                    out = ("Simulation status: "+world.GetSimulationFlag());
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
                        out = ("Entity: "+Entity_manager.Entity_get(Integer.parseInt(str[1].trim()))+" food set to: "+Integer.parseInt(str[2].trim()));
                    }
                    break;
                
                case "Get":
                    if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1])<= Entity_manager.Entity_count()){
                        try {
                            entity temp = Entity_manager.Entity_get(Integer.parseInt(str[1]));
                            StringBuilder tempStr = new StringBuilder();
                            
                            tempStr.append("ID: "+temp.getId()+"/n");
                            tempStr.append("IsAlive: "+temp.isAlive()+"/n");
                            tempStr.append("Healt: "+temp.getHealt()+"/n");
                            tempStr.append("Food: "+temp.getFood()+"/n");
                            tempStr.append("NetRaward: "+temp.getNetreward()+"/n");
                            tempStr.append("Age: "+temp.getAge()+"/n");
                            tempStr.append("Sex: "+temp.getSex()+"/n");
                            tempStr.append("Pos: "+temp.getPos());
                            out = tempStr.toString();
                            
                        } catch (Exception e) { out = ("Errore get Entity: "+ e.getMessage());}
                    }else{out = ("Errore in get input: "+str[1]);}
                    break;
                    
                default:
                    out = ("Parametro: "+str[0]);
                    break;
            }
        }
        return out;
    }
}