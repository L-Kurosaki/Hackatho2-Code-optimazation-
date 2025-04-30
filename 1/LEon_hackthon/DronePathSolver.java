import java.io.*;
import java.util.*;

class DronePathSolver {
    static int zooX, zooY, zooZ;
    static int depotX, depotY, depotZ;
    static int batteryCapacity;
    static int maxRuns = 1;
    static List<FoodStorage> foodStorages = new ArrayList<>();
    static List<Enclosure> enclosures = new ArrayList<>();
    static Set<Enclosure> visitedEnclosures = new HashSet<>();

    public static void main(String[] args) throws IOException {
        readZooFile("1.txt");
        printDebugInfo();
        List<List<int[]>> runs = generateRuns();
        writeOutput(runs);
        printScore(runs.get(0));
    }

    static void readZooFile(String filename) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(filename));
        String[] dims = br.readLine().replace("(", "").replace(")", "").split(",");
        zooX = Integer.parseInt(dims[0].trim());
        zooY = Integer.parseInt(dims[1].trim());
        zooZ = Integer.parseInt(dims[2].trim());

        String[] depot = br.readLine().replace("(", "").replace(")", "").split(",");
        depotX = Integer.parseInt(depot[0].trim());
        depotY = Integer.parseInt(depot[1].trim());
        depotZ = Integer.parseInt(depot[2].trim());

        batteryCapacity = Integer.parseInt(br.readLine().trim());

        String foodLine = br.readLine().trim();
        if (!foodLine.equals("[]")) {
            foodLine = foodLine.substring(1, foodLine.length() - 1);
            for (String part : foodLine.split("\\),\\(")) {
                String[] data = part.replace("(", "").replace(")", "").split(",");
                foodStorages.add(new FoodStorage(
                    Integer.parseInt(data[0].trim()),
                    Integer.parseInt(data[1].trim()),
                    Integer.parseInt(data[2].trim()),
                    data[3].trim().charAt(0)
                ));
            }
        }

        String encLine = br.readLine().trim();
        if (!encLine.equals("[]")) {
            encLine = encLine.substring(1, encLine.length() - 1);
            for (String part : encLine.split("\\),\\(")) {
                String[] data = part.replace("(", "").replace(")", "").split(",");
                enclosures.add(new Enclosure(
                    Integer.parseInt(data[0].trim()),
                    Integer.parseInt(data[1].trim()),
                    Integer.parseInt(data[2].trim()),
                    Double.parseDouble(data[3].trim()),
                    data[4].trim().charAt(0)
                ));
            }
        }
        br.close();
    }

    static void printDebugInfo() {
        System.out.println("Zoo Dimensions: " + zooX + "x" + zooY + "x" + zooZ);
        System.out.println("Drone Depot: (" + depotX + "," + depotY + "," + depotZ + ")");
        System.out.println("Battery Capacity: " + batteryCapacity);
    }

    static List<List<int[]>> generateRuns() {
        List<List<int[]>> runs = new ArrayList<>();
        List<Enclosure> sortedEnclosures = new ArrayList<>(enclosures);
        sortedEnclosures.sort((a, b) -> Double.compare(b.importance, a.importance));

        List<int[]> bestRun = null;
        double bestScore = 0;

        for (FoodStorage fs : foodStorages) {
            List<int[]> run = new ArrayList<>();
            Set<Enclosure> used = new HashSet<>();
            int remainingBattery = batteryCapacity - takeoffLandCost(depotZ);
            int currX = depotX, currY = depotY;

            int toFood = distance(currX, currY, fs.x, fs.y) + takeoffLandCost(fs.z);
            if (toFood > remainingBattery) continue;

            run.add(new int[]{depotX, depotY});
            run.add(new int[]{fs.x, fs.y});
            remainingBattery -= toFood;
            currX = fs.x; currY = fs.y;

            double totalImportance = 0;

            for (Enclosure e : sortedEnclosures) {
                if (e.diet != fs.diet || used.contains(e)) continue;

                int toEnc = distance(currX, currY, e.x, e.y) + takeoffLandCost(e.z);
                int toDepot = distance(e.x, e.y, depotX, depotY) + landingCost(depotZ);

                if (toEnc + toDepot > remainingBattery) continue;

                run.add(new int[]{e.x, e.y});
                remainingBattery -= toEnc;
                totalImportance += e.importance;
                currX = e.x; currY = e.y;
                used.add(e);
            }

            int back = distance(currX, currY, depotX, depotY) + landingCost(depotZ);
            if (back <= remainingBattery) {
                run.add(new int[]{depotX, depotY});
                double score = totalImportance * 1000 - (batteryCapacity - remainingBattery);
                if (score > bestScore) {
                    bestScore = score;
                    bestRun = run;
                }
            }
        }

        if (bestRun != null) runs.add(bestRun);
        return runs;
    }

    static int distance(int x1, int y1, int x2, int y2) {
        return (int) Math.round(Math.sqrt(Math.pow(x2 - x1, 2) + Math.pow(y2 - y1, 2)));
    }

    static int takeoffLandCost(int z) {
        return (50 - z) * 2;
    }

    static int landingCost(int z) {
        return 50 - z;
    }

    static void writeOutput(List<List<int[]>> runs) throws IOException {
        BufferedWriter bw = new BufferedWriter(new FileWriter("output.txt"));
        bw.write("[");
        for (int i = 0; i < runs.size(); i++) {
            List<int[]> path = runs.get(i);
            bw.write("[");
            for (int j = 0; j < path.size(); j++) {
                int[] point = path.get(j);
                bw.write("(" + point[0] + "," + point[1] + ")");
                if (j < path.size() - 1) bw.write(", ");
            }
            bw.write("]");
            if (i < runs.size() - 1) bw.write(", ");
        }
        bw.write("]");
        bw.close();
    }

    static void printScore(List<int[]> run) {
        if (run == null || run.size() < 2) return;

        double importance = 0;
        int totalDistance = 0;
        int prevX = run.get(0)[0], prevY = run.get(0)[1];

        for (int i = 1; i < run.size(); i++) {
            int currX = run.get(i)[0];
            int currY = run.get(i)[1];
            totalDistance += distance(prevX, prevY, currX, currY);
            prevX = currX;
            prevY = currY;

            for (Enclosure e : enclosures) {
                if (e.x == currX && e.y == currY) {
                    importance += e.importance;
                    break;
                }
            }
        }

        double score = importance * 1000 - totalDistance;
        if (score < 0) score = 0;
        System.out.println("Drone run 0 obtained " + score + " points.");
    }

    static class FoodStorage {
        int x, y, z;
        char diet;
        FoodStorage(int x, int y, int z, char diet) {
            this.x = x; this.y = y; this.z = z; this.diet = diet;
        }
    }

    static class Enclosure {
        int x, y, z;
        double importance;
        char diet;
        Enclosure(int x, int y, int z, double importance, char diet) {
            this.x = x; this.y = y; this.z = z;
            this.importance = importance; this.diet = diet;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof Enclosure)) return false;
            Enclosure e = (Enclosure) o;
            return this.x == e.x && this.y == e.y && this.z == e.z;
        }

        @Override
        public int hashCode() {
            return Objects.hash(x, y, z);
        }
    }
}
