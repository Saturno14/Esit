package src;
import java.nio.file.*;
import java.util.Arrays;

public class Saving {
    public boolean SaveEntityBrain(){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();

        for(int i=0; i<Entity_manager.EntityNumber;i++){
            int topology[] = Entity_manager.Entity_get(i).getBrain().getTopology();
            for(int j=0; j<topology.length-1;j++){
                for(int z=0;z<topology[j+1];z++){
                    Str.append(Entity_manager.Entity_get(i).getBrain().getLayer(j).getNeuron(z).getBias());
                    Str.append(" -- ");
                    double weights[] = Entity_manager.Entity_get(i).getBrain().getLayer(j).getNeuron(z).getWeight();
                    for(int c=0;c<weights.length;c++){
                        Str.append(weights[c]+" - ");
                    }
                }
                Str.append("\n");
            }
        }
        try {
            Files.writeString(Path.of("saving/brain.json"), Str.toString());
            flag = true;
        } catch (Exception e) {System.out.println("Errore Saving brain");}

        return flag;
    }

    public boolean SaveEntity(){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();
        for(int v=0; v<Entity_manager.EntityNumber;v++){
            Str.setLength(0);
            Str.append("{\n");
            Str.append("    ID: "+Entity_manager.Entity_get(v).getId()+"\n");
            Str.append("    Age: "+Entity_manager.Entity_get(v).getAge()+"\n");
            Str.append("    Food: "+Entity_manager.Entity_get(v).getFood()+"\n");
            Str.append("    FoodConsumed: "+Entity_manager.Entity_get(v).getFoodConsumed()+"\n");
            Str.append("    Healt: "+Entity_manager.Entity_get(v).getHealt()+"\n");
            Str.append("    Sex: "+Entity_manager.Entity_get(v).getSex()+"\n");
            Str.append("    Fitness: "+ Entity_manager.Entity_get(v).getFitness()+"\n");
            int[] tempos = Entity_manager.Entity_get(v).getPos();
            Str.append("    Pos: "+tempos[0]+" "+tempos[1]+" "+tempos[2]+"\n");
            Str.append("}\n");
            try {
                Files.writeString(Path.of("saving/entity.json"), Str.toString());
                flag = true;
            } catch (Exception e) {System.out.println("Errore Saving brain");}
            if(!flag){
                System.out.println("Errore saving Entity");
                return flag;
            }
            flag = false;
        }
        flag = true;
        return flag;
    }

    public boolean SaveWorld(){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();
        for(int i=0;i<world.getDim();i++){
            for(int j=0; j<world.getDim();j++){
                int[] info = world.cord_Type(i, world.getGround(), j);
                if(info[0]>0){
                    Str.append("Entity: ");
                    for(int f=0;f<Entity_manager.Entity_count();f++){
                        int[] tempPos = {i,world.getGround(),j};
                        if(Arrays.equals(Entity_manager.Entity_get(f).getPos(), tempPos)){
                            Str.append(Entity_manager.Entity_get(f).getId()+" - ");
                        }
                    }
                }
                else if(info[1]>0){
                    Str.append("food: "+info[1]+" - ");
                }

                Str.append("Type: "+info[2]+"\n");
            }
        }
        try {
            Files.writeString(Path.of("saving/world.json"), Str.toString());
            flag = true;
        } catch (Exception e) {System.out.println("Errore Saving brain");}
        return flag;
    }

    public boolean Save(){
        boolean flag = false;
        if(SaveEntity() && SaveEntityBrain() && SaveWorld()){flag = true;}
        return flag;
    }
}
