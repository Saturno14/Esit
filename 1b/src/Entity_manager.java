package src;
import java.util.ArrayList;
import java.util.Random;


public class Entity_manager {
    private static ArrayList<entity> EntityList = new ArrayList<entity>(); 
    private static ArrayList<entity> FullEntityList = new ArrayList<entity>(); 
    public static int EntityNumber = 0;


    public static synchronized entity getRandomPartner(int excludeId, int neededSex){
        ArrayList<entity> candidates = new ArrayList<>();
        for(entity e : EntityList){
            if(e.getId() != excludeId && e.getSex() == neededSex && e.life.get()){
                candidates.add(e);
            }
        }
        if(candidates.isEmpty()) return null;
        return candidates.get(new Random().nextInt(candidates.size()));
    }

    public static synchronized double getAverageFitness(){
        if(EntityList.isEmpty()) return 0;
        double sum = 0;
        for(entity e : EntityList){
            sum += e.getFitness();
        }
        return sum / EntityList.size();
    }

    public static synchronized int Entity_count(){
        return EntityList.size();
    }

    public static synchronized entity Entity_get(int index){
        return EntityList.get(index);
    }

    public static synchronized void Entity_add(entity a){
        EntityList.add(a);
        FullEntityList.add(a);
        EntityNumber++;
    }

    public static synchronized void Entity_remuve(entity a){
        EntityList.remove(a);
    }

    public static synchronized int get_EntityN(){
        return EntityNumber;
    }

    public static synchronized void Entity_remuve_ID(int id){
        for(int i=0;i<EntityList.size();i++){
            if(EntityList.get(i).getId() == id){EntityList.remove(i);}
        }
    }

}
