package src;
import java.io.File;
import java.nio.file.*;
import java.util.Arrays;

public class Saving {
    private static boolean SaveEntityBrain(String dir){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();

        for(int i=0; i<Entity_manager.Entity_count();i++){
            Str.append("{\n");
            int topology[] = Entity_manager.Entity_get(i).getBrain().getTopology();
            for(int j=0; j<topology.length-1;j++){
                for(int z=0;z<topology[j+1];z++){
                    Str.append("    "+Entity_manager.Entity_get(i).getBrain().getLayer(j).getNeuron(z).getBias()+",\n");
                    double weights[] = Entity_manager.Entity_get(i).getBrain().getLayer(j).getNeuron(z).getWeight();
                    for(int c=0;c<weights.length;c++){
                        Str.append("    "+weights[c]+",\n");
                    }
                }
            }
            Str.append("};\n");
        }
        try {
            Files.writeString(Path.of(dir+"/brain.json"), Str.toString());
            flag = true;
        } catch (Exception e) {System.out.println("Errore Saving brain"+e.getMessage());}

        return flag;
    }

    private static boolean SaveEntity(String dir){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();
        for(int v=0; v<Entity_manager.Entity_count();v++){
            Str.append("{\n");
            Str.append("    ID: "+Entity_manager.Entity_get(v).getId()+",\n");
            Str.append("    Age: "+Entity_manager.Entity_get(v).getAge()+",\n");
            Str.append("    Food: "+Entity_manager.Entity_get(v).getFood()+",\n");
            Str.append("    FoodConsumed: "+Entity_manager.Entity_get(v).getFoodConsumed()+",\n");
            Str.append("    Healt: "+Entity_manager.Entity_get(v).getHealt()+",\n");
            Str.append("    Sex: "+Entity_manager.Entity_get(v).getSex()+",\n");
            Str.append("    Fitness: "+ Entity_manager.Entity_get(v).getFitness()+",\n");
            int[] tempos = Entity_manager.Entity_get(v).getPos();
            Str.append("    Pos: "+tempos[0]+" "+tempos[1]+" "+tempos[2]+",\n");
            Str.append("};\n");
        }
        try {
            Files.writeString(Path.of(dir+"/entity.json"), Str.toString());
            flag = true;
        } catch (Exception e) {System.out.println("Errore Saving brain"+e.getMessage());}
        return flag;
    }

    private static boolean SaveWorld(String dir){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();
        for(int i=0;i<world.getDim();i++){
            for(int j=0; j<world.getDim();j++){
                Str.append("{\n");
                int[] info = world.cord_Type(i, world.getGround(), j);
                if(info[0]>0){
                    Str.append("    Entity: \n");
                    for(int f=0;f<Entity_manager.Entity_count();f++){
                        int[] tempPos = {i,world.getGround(),j};
                        if(Arrays.equals(Entity_manager.Entity_get(f).getPos(), tempPos)){
                            Str.append("    "+Entity_manager.Entity_get(f).getId()+"\n");
                        }
                    }
                }
                else if(info[1]>0){
                    Str.append("    food: "+info[1]+"\n");
                }

                Str.append("    Type: "+info[2]+"\n");
                Str.append("};\n");
            }
        }
        try {
            Files.writeString(Path.of(dir+"/world.json"), Str.toString());
            flag = true;
        } catch (Exception e) {System.out.println("Errore Saving brain"+e.getMessage());}
        return flag;
    }

    public static boolean Save(){
        boolean flag = false;
        File root = new File("saving");
        File[] subdirs = root.listFiles(File::isDirectory);


        StringBuilder name = new StringBuilder();
        if(subdirs.length == 0){name.append("s-0");}
        else{name.append(subdirs[subdirs.length-1].getName());}
        String[] str = name.toString().split("-");
        int n = Integer.parseInt(str[1])+1;
        name.replace(name.length()-str[1].length(), name.length(), Integer.toString(n));
        try{
            Files.createDirectories(Path.of("saving/" + name.toString()));
        }catch(Exception e){System.out.println("Errore creazione cartella "+e.getMessage());}
        String dir = "saving/"+name.toString();
        if(SaveEntity(dir)){
            if(SaveEntityBrain(dir)){
                if(SaveWorld(dir)){
                    flag= true;
                }else{System.out.println("Errore SaveWorld");}
            }else{System.out.println("Errore SaveEntityBrain");}
        }else{System.out.println("Errore SaveEntity");}
        return flag;
    }
}
