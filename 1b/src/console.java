package src;

import java.io.File;
import java.util.Arrays;
import java.util.Scanner;

public class console{
    public static String input(String input){
        String out = "";
        int procedure = -1;
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
                    world.PauseCycle(false);
                    break;
                case "stop":
                    world.PauseCycle(true);
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
                    if(Integer.parseInt(str[1])>=0 && Integer.parseInt(str[1]) < Entity_manager.Entity_count()){
                        try {
                            entity temp = Entity_manager.Entity_get(Integer.parseInt(str[1]));
                            StringBuilder tempStr = new StringBuilder();
                            
                            tempStr.append("ID: "+temp.getId()+"\n");
                            tempStr.append("IsAlive: "+temp.isAlive()+"\n");
                            tempStr.append("Healt: "+temp.getHealt()+"\n");
                            tempStr.append("Food: "+temp.getFood()+"\n");
                            tempStr.append("Food_consumed: "+temp.getFoodConsumed()+"\n");
                            tempStr.append("NetRaward: "+temp.getNetreward()+"\n");
                            tempStr.append("Age: "+temp.getAge()+"\n");
                            tempStr.append("Sex: "+temp.getSex()+"\n");
                            tempStr.append("Pos: "+temp.getPos().toString());
                            out = tempStr.toString();
                            
                        } catch (Exception e) { out = ("Errore get Entity: "+ e.getMessage());}
                    }else{out = ("Errore in get input: "+str[1]);}
                    break;

                case "NewStart":
                    int start_entity = 10;
                    int Entity_count = 0;
                    int ground;
                    world.world_setup();
                    world.PauseCycle(false);
                    ground = world.ground_search();
                    
                    

                    for(int i=0; i<=start_entity;i++){
                        entity tempEntity = new entity(Entity_count,world.getCycle(),(int)(Math.random()*(world.getDim()-1))+1,ground,(int)(Math.random()*(world.getDim()-1))+1);
                        Entity_manager.Entity_add(tempEntity);
                        int[] temppos = Entity_manager.Entity_get(Entity_manager.Entity_count()-1).getPos();
                        world.add(temppos[0],temppos[1],temppos[2],"E");
                        System.out.println("Entity: "+Entity_manager.Entity_get(i).getId()+" "+Arrays.toString(Entity_manager.Entity_get(i).getPos()));
                        new Thread(()->{
                            String temp = "";
                            temp = Thread.currentThread().getName();
                            tempEntity.setProcessId(temp);
                            tempEntity.GoLife();
                        }).start();
                        Entity_count++;
                    }
                    Entity_count = Entity_manager.Entity_count();
                    for(int i=0;i<20; i++){
                        world.add((int)(Math.random()*20), ground, (int)(Math.random()*20), "M");
                    }
                    world.PauseCycle(false);
                    world.DoCycle();
                    break;

                case "Save":
                    try {
                        world.PauseCycle(true);
                        Thread.sleep(5000);
                        Saving.Save();
                        world.PauseCycle(false);
                    } catch (Exception e) {System.out.println("Errore Save "+e.getMessage());}
                    out = "Saved";
                    break;

                case "Load":
                    world.PauseCycle(true);
                    System.out.println("Lista Salvataggi");
                    File root = new File("saving/");
                    File[] subdirs = root.listFiles(File::isDirectory);
                    int index = 0;
                    for(File s : subdirs){
                        System.out.println(index +"] "+s.getName());
                        index++;
                    }

                    System.out.print("Quale salvataggio vuoi caricare: ");
                    Scanner scanner = new Scanner(System.in);
                    int n = scanner.nextInt();
                    if(n>=0 && n<=index-1){
                        if(Saving.Load(n, subdirs)){out = "Salvataggio caricato";}
                        else{out= "errore caricamento salvataggio";}
                    }else{System.out.println("Numero salvataggio errato");}
                    break;
                    
                default:
                    out = ("Parametro: "+str[0]);
                    break;
            }
        }
        return out;
    }
    
}