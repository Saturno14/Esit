package src;
import java.util.ArrayList;

public class Entity_manager {
    private static ArrayList<entity> EntityList = new ArrayList<entity>(); 


    public static synchronized int Entity_count(){
        return EntityList.size();
    }

    public static synchronized entity Entity_get(int index){
        return EntityList.get(index);
    }

    public static synchronized void Entity_add(entity a){
        EntityList.add(a);
    }

    public static synchronized void Entity_remuve(entity a){
        EntityList.remove(a);
    }

    public static synchronized void Entity_remuve_ID(int id){
        for(int i=0;i<=EntityList.size();i++){
            if(EntityList.get(i).getId() == id){EntityList.remove(i);}
        }
    }

}
