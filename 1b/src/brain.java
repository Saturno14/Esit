package src;
import java.util.Random;
public class brain {

    public static class Neuron {

        private double[] weights;
        private double bias;

        public Neuron(int inputCount) {

            Random random = new Random();

            weights = new double[inputCount];

            for (int i = 0; i < inputCount; i++) {
                weights[i] = random.nextDouble() * 2 - 1;
            }

            bias = random.nextDouble() * 2 - 1;
        }


        public Neuron copy(){
            Neuron clone = new Neuron(weights.length);
            for(int i=0;i<weights.length;i++){
                clone.weights[i] = weights[i];
            }
            clone.bias = bias;

            return clone;
        }

        public void setBias(double baias){
            this.bias = baias;
        }

        public void setWeights(double[] weiggts){
            for (int i = 0; i < weiggts.length; i++) {
                weights[i] = weiggts[i];
            }
        }

        public void mutate(double rate){

            Random random = new Random();
            for(int i=0;i<weights.length;i++){
                if(random.nextDouble() < 0.1){
                    weights[i] += random.nextGaussian()*rate;
                }
            }
            if(random.nextDouble()<0.1){
                bias += random.nextGaussian()*rate;
            }
        }

        public double calculate(double[] input) {

            double sum = bias;
            for (int i = 0; i < input.length; i++) {
                sum += input[i] * weights[i];
            }
            return sigmoid(sum);
        }

        private double sigmoid(double x) {
            return 1.0 / (1.0 + Math.exp(-x));
        }

        public int inputSize(){
            return weights.length;
        }

        public double[] getWeight(){
            return weights;
        }

        public double getBias(){
            return bias;
        }

    }


   public static class Layer {
        private Neuron[] neurons;

        public Layer(int neuronCount, int inputCount) {

            neurons = new Neuron[neuronCount];

            for (int i = 0; i < neuronCount; i++) {
                neurons[i] = new Neuron(inputCount);
            }
        }

        public int size(){
            return neurons.length;
        }

        public int inputSize(){
            return neurons[0].inputSize();
        }

        public Layer copy(){
            Layer clone = new Layer(neurons.length, neurons[0].inputSize());
            for(int i=0;i<neurons.length;i++){
                clone.neurons[i] = neurons[i].copy();
            }
            return clone;
        }

        public void mutate(double rate){
            for(Neuron n : neurons){
                n.mutate(rate);
            }
        }

        public Neuron getNeuron(int index){
            return neurons[index];
        }
                
        public double[] calculate(double[] input) {

            double[] output = new double[neurons.length];

            for (int i = 0; i < neurons.length; i++) {
                output[i] = neurons[i].calculate(input);
            }

            return output;
        }
    }

   public static class NeuralNetwork {

        private Layer[] layers;
        private int[] topology;

        public NeuralNetwork(int... topology) {
            this.topology = topology.clone();

            layers = new Layer[topology.length - 1];

            for (int i = 0; i < layers.length; i++) {

                layers[i] = new Layer(
                        topology[i + 1],
                        topology[i]
                );

            }
        }

        public int[] getTopology(){
            return topology;
        }

        public Layer getLayer(int index){
            return layers[index];
        }

        public void mutate(double rate){
            for(Layer l : layers){
                l.mutate(rate);
            }
        }

        public double[] predict(double[] input) {
            double[] output = input;
            for (Layer layer : layers) {
                output = layer.calculate(output);
            }
            return output;
        }

        public NeuralNetwork copy(){
            NeuralNetwork clone = new NeuralNetwork(topology);
            for(int i=0;i<layers.length;i++){
                clone.layers[i]=layers[i].copy();
            }
            return clone;
        }
   }

   public static class Reproduction {
        public static NeuralNetwork createChild(NeuralNetwork parent){
            NeuralNetwork child = parent.copy();
            child.mutate(0.1);
            return child;
        }

        public static NeuralNetwork crossover(NeuralNetwork parentA, NeuralNetwork parentB, double mutationRate){
            NeuralNetwork child = parentA.copy();
            Random r = new Random();
            for(int l = 0; l < child.layers.length; l++){
                for(int n = 0; n < child.layers[l].neurons.length; n++){
                    if(r.nextBoolean()){ // 50% dei neuroni presi dal genitore B
                        child.layers[l].neurons[n] = parentB.layers[l].neurons[n].copy();
                    }
                }
            }
            child.mutate(mutationRate);
            return child;
        }

        public static NeuralNetwork Partenogenesi(NeuralNetwork parentA, double mutationRate){
            NeuralNetwork child = parentA.copy();
            Random r = new Random();
            child.mutate(mutationRate);
            return child;
        }
    }


        
}
