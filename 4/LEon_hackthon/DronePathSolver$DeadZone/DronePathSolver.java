import java.io.*;
import java.util.*;

class DronePathSolver {
    static int zooX, zooY, zooZ;
    static int depotX, depotY, depotZ;
    static int batteryCapacity;
    static int maxRuns = 250;
    static List<FoodStorage> foodStorages = new ArrayList<>();
    static List<Enclosure> enclosures = new ArrayList<>();
    static List<Deadzone> deadzones = new ArrayList<>();
    static Set<Enclosure> visitedEnclosures = new HashSet<>();

    public static void main(String[] args) throws IOException {
        readZooFile("zoo.txt");
        printDebugInfo();
        List<List<int[]>> runs = generateRuns();
        writeOutput(runs);
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

        String deadLine = br.readLine().trim();
        if (!deadLine.equals("[]")) {
            deadLine = deadLine.substring(1, deadLine.length() - 1);
            for (String part : deadLine.split("\\),\\(")) {
                String[] data = part.replace("(", "").replace(")", "").split(",");
                deadzones.add(new Deadzone(
                    Integer.parseInt(data[0].trim()),
                    Integer.parseInt(data[1].trim()),
                    Double.parseDouble(data[2].trim())
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

        while (runs.size() < maxRuns && visitedEnclosures.size() < enclosures.size()) {
            List<int[]> run = new ArrayList<>();
            run.add(new int[]{depotX, depotY});

            int remainingBattery = batteryCapacity - takeoffLandCost(depotZ);
            int currX = depotX, currY = depotY;
            char carriedFood = '-';

            FoodStorage bestFood = null;
            double bestScore = -1;

            for (FoodStorage fs : foodStorages) {
                int toFood = distance(currX, currY, fs.x, fs.y) + takeoffLandCost(fs.z);
                if (toFood > remainingBattery || segmentIntersectsAnyDeadzone(currX, currY, fs.x, fs.y)) continue;

                int tempX = fs.x, tempY = fs.y;
                int batteryAfterFood = remainingBattery - toFood;
                double score = 0;

                for (Enclosure e : sortedEnclosures) {
                    if (visitedEnclosures.contains(e) || e.diet != fs.diet) continue;
                    int toEnc = distance(tempX, tempY, e.x, e.y) + takeoffLandCost(e.z);
                    int back = distance(e.x, e.y, depotX, depotY) + landingCost(depotZ);
                    if (toEnc + back > batteryAfterFood || segmentIntersectsAnyDeadzone(tempX, tempY, e.x, e.y)) continue;
                    batteryAfterFood -= toEnc;
                    tempX = e.x; tempY = e.y;
                    score += e.importance * 1000;
                }

                if (score > bestScore) {
                    bestScore = score;
                    bestFood = fs;
                }
            }

            if (bestFood != null) {
                int toFood = distance(currX, currY, bestFood.x, bestFood.y) + takeoffLandCost(bestFood.z);
                currX = bestFood.x;
                currY = bestFood.y;
                carriedFood = bestFood.diet;
                run.add(new int[]{currX, currY});
                remainingBattery -= toFood;

                for (Enclosure e : sortedEnclosures) {
                    if (visitedEnclosures.contains(e) || e.diet != carriedFood) continue;

                    int toEnc = distance(currX, currY, e.x, e.y) + takeoffLandCost(e.z);
                    int back = distance(e.x, e.y, depotX, depotY) + landingCost(depotZ);
                    if (toEnc + back > remainingBattery || segmentIntersectsAnyDeadzone(currX, currY, e.x, e.y)) continue;

                    run.add(new int[]{e.x, e.y});
                    remainingBattery -= toEnc;
                    currX = e.x; currY = e.y;
                    visitedEnclosures.add(e);
                }

                int toDepot = distance(currX, currY, depotX, depotY) + landingCost(depotZ);
                if (toDepot <= remainingBattery && !segmentIntersectsAnyDeadzone(currX, currY, depotX, depotY)) {
                    run.add(new int[]{depotX, depotY});
                    runs.add(run);
                }
            }
        }
        return runs;
    }

    static boolean segmentIntersectsAnyDeadzone(int x1, int y1, int x2, int y2) {
        for (Deadzone d : deadzones) {
            if (segmentCircleIntersect(x1, y1, x2, y2, d.x, d.y, d.radius)) {
                return true;
            }
        }
        return false;
    }

    static boolean segmentCircleIntersect(int x1, int y1, int x2, int y2, int cx, int cy, double r) {
        double dx = x2 - x1, dy = y2 - y1;
        double fx = x1 - cx, fy = y1 - cy;

        double a = dx * dx + dy * dy;
        double b = 2 * (fx * dx + fy * dy);
        double c = fx * fx + fy * fy - r * r;

        double discriminant = b * b - 4 * a * c;
        if (discriminant < 0) return false;

        discriminant = Math.sqrt(discriminant);
        double t1 = (-b - discriminant) / (2 * a);
        double t2 = (-b + discriminant) / (2 * a);

        return (t1 >= 0 && t1 <= 1) || (t2 >= 0 && t2 <= 1);
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

    static class Deadzone {
        int x, y;
        double radius;
        Deadzone(int x, int y, double radius) {
            this.x = x; this.y = y; this.radius = radius;
        }
    }
}
