package src;

import org.json.JSONArray;
import org.json.JSONObject;

import src.brain.NeuralNetwork;

/** Serializzazione JSON di una rete neurale (topologia, bias e pesi di ogni strato). */
public final class BrainIO {

    private BrainIO() {
    }

    public static JSONObject toJson(NeuralNetwork net) {
        int[] topology = net.getTopology();
        JSONObject root = new JSONObject();

        JSONArray topo = new JSONArray();
        for (int t : topology) {
            topo.put(t);
        }
        root.put("topology", topo);

        JSONArray layers = new JSONArray();
        for (int l = 0; l < topology.length - 1; l++) {
            brain.Layer layer = net.getLayer(l);
            JSONArray biases = new JSONArray();
            JSONArray weights = new JSONArray();
            for (int n = 0; n < layer.size(); n++) {
                brain.Neuron neuron = layer.getNeuron(n);
                biases.put(neuron.getBias());
                JSONArray row = new JSONArray();
                for (double w : neuron.getWeight()) {
                    row.put(w);
                }
                weights.put(row);
            }
            JSONObject layerObj = new JSONObject();
            layerObj.put("biases", biases);
            layerObj.put("weights", weights);
            layers.put(layerObj);
        }
        root.put("layers", layers);
        return root;
    }

    public static NeuralNetwork fromJson(JSONObject obj) {
        JSONArray topo = obj.getJSONArray("topology");
        int[] topology = new int[topo.length()];
        for (int i = 0; i < topology.length; i++) {
            topology[i] = topo.getInt(i);
        }
        NeuralNetwork net = new NeuralNetwork(topology);

        JSONArray layers = obj.getJSONArray("layers");
        for (int l = 0; l < layers.length(); l++) {
            JSONObject layerObj = layers.getJSONObject(l);
            JSONArray biases = layerObj.getJSONArray("biases");
            JSONArray weights = layerObj.getJSONArray("weights");
            brain.Layer layer = net.getLayer(l);
            for (int n = 0; n < layer.size(); n++) {
                brain.Neuron neuron = layer.getNeuron(n);
                neuron.setBias(biases.getDouble(n));
                JSONArray row = weights.getJSONArray(n);
                double[] w = new double[row.length()];
                for (int k = 0; k < w.length; k++) {
                    w[k] = row.getDouble(k);
                }
                neuron.setWeights(w);
            }
        }
        return net;
    }
}
