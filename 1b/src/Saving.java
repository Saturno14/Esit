package src;
import java.nio.file.*;
import java.io.IOException;
public class Saving {
    public boolean SaveEntity(){
        boolean flag = false;
        StringBuilder Str = new StringBuilder();
        //Files.writeString(Path.of("saving\brain.json"), Str.toString());
        for(int i=0; i<Entity_manager.Entity_count();i++){
            int topology[] = Entity_manager.Entity_get(i).getBrain().getTopology();
            for(int j=0; j<topology.length;j++){
                for(int z=0;z<topology[j];z++){
                    for(b=0;b<Entity_manager.Entity_get(i).getBrain().getLayer(z).)
                }
            }
        }
        return flag;
    }
}
