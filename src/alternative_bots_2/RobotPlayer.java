package alternative_bots_2;

import java.util.Random;

import battlecode.common.Clock;
import battlecode.common.Direction;
import battlecode.common.GameActionException;
import battlecode.common.MapInfo;
import battlecode.common.MapLocation;
import battlecode.common.Message;
import battlecode.common.PaintType;
import battlecode.common.RobotController;
import battlecode.common.RobotInfo;
import battlecode.common.UnitType;


/**
 * RobotPlayer is the class that describes your main robot strategy.
 * The run() method inside this class is like your main function: this is what we'll call once your robot
 * is created!
 */
public class RobotPlayer {
    /**
     * We will use this variable to count the number of turns this robot has been alive.
     * You can use static variables like this to save any information you want. Keep in mind that even though
     * these variables are static, in Battlecode they aren't actually shared between your robots.
     */
    static int turnCount = 0;
    static int spawnedRobots = 0;
    static int spawnedSplasher = 0;
    static int spawnedMopper = 0;
    static int spawnedSoldier = 0;

    static boolean hasAssignedDirection = false;
    static Direction targetDir = null;

    static MapInfo lastRuin = null;
    /**
     * A random number generator.
     * We will use this RNG to make some random moves. The Random class is provided by the java.util.Random
     * import at the top of this file. Here, we *seed* the RNG with a constant number (6147); this makes sure
     * we get the same sequence of numbers every time this code is run. This is very useful for debugging!
     */
    static final Random rng = new Random(6147);

    /** Array containing all the possible movement directions. */
    static final Direction[] directions = {
        Direction.NORTH,
        Direction.NORTHEAST,
        Direction.EAST,
        Direction.SOUTHEAST,
        Direction.SOUTH,
        Direction.SOUTHWEST,
        Direction.WEST,
        Direction.NORTHWEST,
    };

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * It is like the main function for your robot. If this method returns, the robot dies!
     *
     * @param rc  The RobotController object. You use it to perform actions from this robot, and to get
     *            information on its current status. Essentially your portal to interacting with the world.
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {
        // Hello world! Standard output is very useful for debugging.
        // Everything you say here will be directly viewable in your terminal when you run a match!
        System.out.println("I'm alive");

        // You can also use indicators to save debug notes in replays.
        rc.setIndicatorString("Hello world!");

        while (true) {
            // This code runs during the entire lifespan of the robot, which is why it is in an infinite
            // loop. If we ever leave this loop and return from run(), the robot dies! At the end of the
            // loop, we call Clock.yield(), signifying that we've done everything we want to do.

            turnCount += 1;  // We have now been alive for one more turn!

            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode.
            try {
                // The same run() function is called for every robot on your team, even if they are
                // different types. Here, we separate the control depending on the UnitType, so we can
                // use different strategies on different robots. If you wish, you are free to rewrite
                // this into a different control structure!
                switch (rc.getType()){
                    case SOLDIER: runSoldier(rc); break; 
                    case MOPPER: runMopper(rc); break;
                    case SPLASHER: runSplasher(rc); break; // Consider upgrading examplefuncsplayer to use splashers!
                    default: runTower(rc); break;
                    }
                }
             catch (GameActionException e) {
                // Oh no! It looks like we did something illegal in the Battlecode world. You should
                // handle GameActionExceptions judiciously, in case unexpected events occur in the game
                // world. Remember, uncaught exceptions cause your robot to explode!
                System.out.println("GameActionException");
                e.printStackTrace();

            } catch (Exception e) {
                // Oh no! It looks like our code tried to do something bad. This isn't a
                // GameActionException, so it's more likely to be a bug in our code.
                System.out.println("Exception");
                e.printStackTrace();

            } finally {
                // Signify we've done everything we want to do, thereby ending our turn.
                // This will make our code wait until the next turn, and then perform this loop again.
                Clock.yield();
            }
            // End of loop: go back to the top. Clock.yield() has ended, so it's time for another turn!
        }

        // Your code should never reach here (unless it's intentional)! Self-destruction imminent...
    }

    /**
     * Mengecek apakah ada Paint Tower di dekat robot saat cat kritis.
     * Jika ada, robot akan mampir mendekat. Fungsi ini void (tidak menghentikan turn).
     */
    public static boolean tryRefillPaint(RobotController rc) throws GameActionException {
        double paintThreshold = 0.2; // Mulai cari Tower saat bensin tinggal 30%
        
        if (rc.getPaint() < rc.getType().paintCapacity * paintThreshold) {
            // Cari tower terdekat
            RobotInfo[] nearbyAllies = rc.senseNearbyRobots(-1, rc.getTeam());
            
            for (RobotInfo ally : nearbyAllies) {
                UnitType type = ally.getType();
                if (type == UnitType.LEVEL_ONE_PAINT_TOWER || 
                    type == UnitType.LEVEL_TWO_PAINT_TOWER || 
                    type == UnitType.LEVEL_THREE_PAINT_TOWER) { // Cara lebih rapi mengecek apakah itu Tower
                    
                    int distSq = rc.getLocation().distanceSquaredTo(ally.getLocation());
                    
                    // 1. CEK JARAK: Harus <= 2 agar bisa transfer!
                    if (distSq <= 2) {
                        rc.setIndicatorString("Menyedot Cat!");
                        
                        int amountNeeded = rc.getType().paintCapacity - rc.getPaint();
                        
                        // 2. TENTUKAN JUMLAH: Ambil sebutuhnya, atau sedot SEMUA sisa cat tower jika tidak cukup
                        int amountToTake = Math.min(amountNeeded, ally.paintAmount);
                        
                        // 3. ROBOT MENYEDOT CAT: Gunakan nilai NEGATIF (-amountToTake)
                        if (amountToTake > 0 && rc.canTransferPaint(ally.getLocation(), -amountToTake)) {
                            rc.transferPaint(ally.getLocation(), -amountToTake);
                            System.out.println(rc.getType() + " berhasil menyedot " + amountToTake + " cat dari Tower!");
                        }
                        
                        return true; // Sudah nyampe, tetap true agar tidak kelayapan
                    } 
                    // 3. JIKA MASIH JAUH, JALAN MENDEKAT
                    else {
                        rc.setIndicatorString("OTW Tower!");
                        if (rc.isMovementReady()) {
                            Direction dirToTower = rc.getLocation().directionTo(ally.getLocation());
                            
                            Direction[] tryDirs = {dirToTower, dirToTower.rotateLeft(), dirToTower.rotateRight()};
                            Direction bestDir = getValidMove(rc, tryDirs);
                            
                            if (bestDir != null) {
                                rc.move(bestDir);
                            }
                        }
                        return true; 
                    }
                }
                if(type.isTowerType()){
                    if (rc.getChips() > 2500 && rc.canUpgradeTower(ally.getLocation())) {
                        rc.upgradeTower(ally.getLocation());
                        System.out.println("UPGRADE! " + rc.getType() + " menaikkan level Tower di " + ally.getLocation());
                    }
                }
            }
        }
        return false; // Cat aman atau tidak ada Tower
    }

    /**
     * Run a single turn for towers.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runTower(RobotController rc) throws GameActionException{
        // Pick a direction to build in.
        Direction dir = directions[rng.nextInt(directions.length)];
        MapLocation nextLoc = rc.getLocation().add(dir);
        // Pick a random robot type to build.
        // 0 -> Soldier, 1 -> Mopper, 2 -> Splasher.
        int robotType;
        if(spawnedRobots < 6){
            robotType = 2;
        }
        else{
            int roll = rng.nextInt(100);
            if(roll >= 25 && roll < 98){
                robotType = 2;
            }
            else if(roll < 25 && roll >= 0){
                robotType = 0;
            }
            else{
                robotType = 1;
            }
        }
        if (robotType == 0 && rc.canBuildRobot(UnitType.SOLDIER, nextLoc)){
            rc.buildRobot(UnitType.SOLDIER, nextLoc);
            System.out.println("BUILT A SOLDIER");
            spawnedRobots++;
        }
        else if (robotType == 1 && rc.canBuildRobot(UnitType.MOPPER, nextLoc)){
            rc.buildRobot(UnitType.MOPPER, nextLoc);
            System.out.println("BUILT A MOPPER");
            spawnedRobots++;
        }
        else if (robotType == 2 && rc.canBuildRobot(UnitType.SPLASHER, nextLoc)){
            rc.buildRobot(UnitType.SPLASHER, nextLoc);
            System.out.println("BUILT A SPLASHER");
            rc.setIndicatorString("SPLASHER NOT IMPLEMENTED YET");
            spawnedRobots++;
        }
        Message[] messages = rc.readMessages(-1);
        for (Message m : messages) {
            System.out.println("Tower received message: '#" + m.getSenderID() + " " + m.getBytes());
        }

        RobotInfo[] enemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());

        // 2. Jika ada musuh yang terdeteksi, bersiap menyerang
        if (enemies.length > 0) {
            for (RobotInfo enemy : enemies) {
                MapLocation enemyLoc = enemy.getLocation();
                
                // 3. Pastikan tower bisa menembak musuh ini (masuk range & tidak cooldown)
                if (rc.canAttack(enemyLoc)) {
                    rc.attack(enemyLoc);
                    System.out.println("Tower menembak musuh (" + enemy.getType() + ") di " + enemyLoc);
                    
                    // Tower biasanya hanya bisa menyerang 1 kali per turn, 
                    // jadi kita hentikan loop-nya di sini agar hemat bytecode.
                    break; 
                }
            }
        }
    }
    public static void runSplasher(RobotController rc) throws GameActionException {
        boolean isRefilling = tryRefillPaint(rc);
        if(!hasAssignedDirection){
            // 1. Dapatkan posisi saat ini dan posisi tengah peta
            MapLocation myLoc = rc.getLocation();
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            
            // directionTo otomatis menghitung selisih X dan Y lalu mengembalikan arah utama
            Direction dirToCenter = myLoc.directionTo(center);
            
            // Cegah error jika robot sudah berada tepat di titik tengah (selisih X=0, Y=0)
            // if (dirToCenter == Direction.CENTER) {
            //     dirToCenter = directions[rng.nextInt(8)];
            // }
            
            // 2. Buat Grup Arah berdasarkan imajinasi Anda
            // Grup 70%: Arah utama ke tengah + serong kiri + serong kanan (3 arah)
            Direction[] forwardDirs = {
                dirToCenter, 
                dirToCenter.rotateLeft(), 
                dirToCenter.rotateRight()
            };
            
            // Grup 30%: 5 arah sisanya (arah berlawanan dan sekitarnya)
            Direction[] otherDirs = {
                dirToCenter.rotateLeft().rotateLeft(),
                dirToCenter.rotateLeft().rotateLeft().rotateLeft(),
                dirToCenter.opposite(),
                dirToCenter.rotateRight().rotateRight(),
                dirToCenter.rotateRight().rotateRight().rotateRight()
            };
            
            // 3. Logika Probabilitas (Roll Dadu 0-99)
            int roll = rng.nextInt(100);
            
            if (roll < 60) {
                // Peluang 70%: Pilih acak 1 dari 3 arah ke depan (masing-masing peluang 1/3)
                targetDir = forwardDirs[rng.nextInt(forwardDirs.length)];
                rc.setIndicatorString("70% Area (Forward)");
            } else {
                // Peluang 30%: Pilih acak 1 dari 5 arah lainnya (masing-masing peluang 1/5)
                targetDir = otherDirs[rng.nextInt(otherDirs.length)];
                rc.setIndicatorString("30% Area (Opposite)");
            }
            hasAssignedDirection = true;
       }
        
        Direction moveDir = targetDir;
        if (!rc.canMove(moveDir)) {
            // Coba cari jalan acak lain untuk turn ini agar tidak diam
            moveDir = getRandomValidMove(rc);
            
            // Hapus ingatan! Agar turn depan dia menghitung ulang arah 70/30 yang baru
            hasAssignedDirection = false; 
        }
        
        // ==========================================================
        // 2. UBAH BAGIAN INI: Cek isRefilling sebelum memanggil rc.move
        // ==========================================================
        if (!isRefilling) { // <--- REM TANGAN: Jangan jalan kalau sedang isi bensin!
            if (moveDir != null && rc.canMove(moveDir)) {
                rc.move(moveDir);
            }
        }
        
        // 6. Eksekusi Pengecatan (Sambil Jalan)
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        // Mengecat area 3x3 di sekitar robot JIKA lantai tempatnya berdiri bukan cat sekutu
        if (!currentTile.getPaint().isAlly()) {
            if (rc.canAttack(rc.getLocation())) {
                rc.attack(rc.getLocation());
            }
        }
    }

    // --- FUNGSI PEMBANTU (Tetap taruh di dalam class RobotPlayer) ---
    private static Direction getRandomValidMove(RobotController rc) throws GameActionException {
        int startIdx = rng.nextInt(directions.length);
        for (int i = 0; i < directions.length; i++) {
            Direction dir = directions[(startIdx + i) % directions.length];
            if (dir != Direction.CENTER && rc.canMove(dir)) {
                return dir;
            }
        }
        return null;
    }
    private static Direction getValidMove(RobotController rc, Direction[] preferredDirs) throws GameActionException {
        for (Direction dir : preferredDirs) {
            if (dir != Direction.CENTER && rc.canMove(dir)) {
                return dir; // Kembalikan arah pertama yang valid
            }
        }
        return null; // Mengembalikan null jika mentok semua
    }

    /**
     * Run a single turn for a Soldier.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runSoldier(RobotController rc) throws GameActionException{
        boolean isRefilling = tryRefillPaint(rc);
        // Sense information about all visible nearby tiles.
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        // Search for a nearby ruin to complete.
        MapInfo curRuin = null;
        boolean hasEnemyPaint = false;
        for (MapInfo tile : nearbyTiles){
            if (tile.hasRuin()){
                MapLocation ruinLoc = tile.getMapLocation();
                
                // Cek apakah di koordinat reruntuhan ini KOSONG (tidak ada Tower/Robot)
                if (rc.senseRobotAtLocation(ruinLoc) == null) {
                    curRuin = tile;
                    MapInfo[] ruinArea = rc.senseNearbyMapInfos(ruinLoc, 8);
            
                    for (MapInfo areaTile : ruinArea) {
                        // Jika ketemu minimal 1 cat musuh di area tersebut
                        if (areaTile.getPaint().isEnemy()) {
                            hasEnemyPaint = true;
                            break; // Hemat komputasi, hentikan pencarian karena sudah pasti ada cat musuh
                        }
                    }
                    // ==================================================
                    
                    // Jika Anda hanya butuh 1 Ruin, Anda bisa menambahkan 'break;' di sini
                    // agar robot tidak perlu repot-repot mengecek Ruin lain yang lebih jauh.
                    break;
                }
            }
        }
        if (curRuin != null && curRuin != lastRuin && !hasEnemyPaint){
            MapLocation targetLoc = curRuin.getMapLocation();
            Direction dir = rc.getLocation().directionTo(targetLoc);
            if (!isRefilling && rc.canMove(dir)) {
                rc.move(dir);
            }
            // Mark the pattern we need to draw to build a tower here if we haven't already.
            MapLocation shouldBeMarked = curRuin.getMapLocation().subtract(dir);
            
            // Hanya acak dan buat marka JIKA lantai tersebut masih kosong
            if (rc.senseMapInfo(shouldBeMarked).getMark() == PaintType.EMPTY) {
                
                // Roll dadu 0-99 untuk menentukan tipe tower
                UnitType typeToBuild;
                int roll = rng.nextInt(100);
                
                if (roll < 58) {
                    typeToBuild = UnitType.LEVEL_ONE_PAINT_TOWER; // Peluang 50%
                } else {
                    typeToBuild = UnitType.LEVEL_ONE_MONEY_TOWER; // Peluang 50%
                }

                // Tandai blueprint sesuai tipe yang terpilih
                if (rc.canMarkTowerPattern(typeToBuild, targetLoc)) {
                    rc.markTowerPattern(typeToBuild, targetLoc);
                    System.out.println("Mencoba membangun " + typeToBuild + " di " + targetLoc);
                }
            }
            // Fill in any spots in the pattern with the appropriate paint.
            for (MapInfo patternTile : rc.senseNearbyMapInfos(targetLoc, 8)){
                if (patternTile.getMark() != patternTile.getPaint() && patternTile.getMark() != PaintType.EMPTY){
                    boolean useSecondaryColor = patternTile.getMark() == PaintType.ALLY_SECONDARY;
                    if (rc.canAttack(patternTile.getMapLocation()))
                        rc.attack(patternTile.getMapLocation(), useSecondaryColor);
                }
            }
            // Complete the ruin if we can.
            if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc)){
                rc.completeTowerPattern(UnitType.LEVEL_ONE_PAINT_TOWER, targetLoc);
                rc.setTimelineMarker("Paint Tower Built", 0, 255, 0); // Warna penanda: Hijau
                System.out.println("Berhasil membangun Paint Tower di " + targetLoc + "!");
                lastRuin = curRuin;
            } 
            // Jika bukan Paint, cek apakah Money Tower yang selesai?
            else if (rc.canCompleteTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc)){
                rc.completeTowerPattern(UnitType.LEVEL_ONE_MONEY_TOWER, targetLoc);
                rc.setTimelineMarker("Money Tower Built", 255, 215, 0); // Warna penanda: Emas/Kuning
                System.out.println("Berhasil membangun Money Tower di " + targetLoc + "!");
                lastRuin = curRuin;
            }
        }
        // RobotInfo targetEnemyTower = null;

        // // 1. Pindai semua robot musuh dalam jarak pandang
        // RobotInfo[] visibleEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());

        // // 2. Cari robot yang merupakan tipe Tower
        // for (RobotInfo enemy : visibleEnemies) {
        //     if (enemy.getType().isTowerType()) {
        //         targetEnemyTower = enemy;
        //         break; // Dapatkan tower pertama yang terdeteksi
        //     }
        // }

        // // 3. Jika Tower musuh ditemukan, lakukan aksi
        // if (targetEnemyTower != null) {
        //     MapLocation towerLoc = targetEnemyTower.getLocation();
        //     Direction dirToTower = rc.getLocation().directionTo(towerLoc);
            
        //     // A. Coba bergerak mendekati tower (jika belum cooldown jalan & tidak terhalang)
        //     if (rc.canMove(dirToTower)) {
        //         rc.move(dirToTower);
        //     }
            
        //     // B. Coba serang tower (jika jaraknya sudah masuk jangkauan attack)
        //     if (rc.canAttack(towerLoc)) {
        //         rc.attack(towerLoc);
        //         rc.setIndicatorString("Menyerang Tower Musuh di " + towerLoc);
        //     }
            
        //     // Jika Anda memakai ini di dalam fungsi terpisah, Anda bisa me-return true di sini
        //     // return true; 
        // }

        // Move and attack randomly if no objective.
        if(!hasAssignedDirection){
            // 1. Dapatkan posisi saat ini dan posisi tengah peta
            MapLocation myLoc = rc.getLocation();
            MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
            
            // directionTo otomatis menghitung selisih X dan Y lalu mengembalikan arah utama
            Direction dirToCenter = myLoc.directionTo(center);
            
            // Cegah error jika robot sudah berada tepat di titik tengah (selisih X=0, Y=0)
            // if (dirToCenter == Direction.CENTER) {
            //     dirToCenter = directions[rng.nextInt(8)];
            // }
            
            // 2. Buat Grup Arah berdasarkan imajinasi Anda
            // Grup 70%: Arah utama ke tengah + serong kiri + serong kanan (3 arah)
            Direction[] forwardDirs = {
                dirToCenter, 
                dirToCenter.rotateLeft(), 
                dirToCenter.rotateRight()
            };
            
            // Grup 30%: 5 arah sisanya (arah berlawanan dan sekitarnya)
            Direction[] otherDirs = {
                dirToCenter.rotateLeft().rotateLeft(),
                dirToCenter.rotateLeft().rotateLeft().rotateLeft(),
                dirToCenter.opposite(),
                dirToCenter.rotateRight().rotateRight(),
                dirToCenter.rotateRight().rotateRight().rotateRight()
            };
            
            // 3. Logika Probabilitas (Roll Dadu 0-99)
            int roll = rng.nextInt(100);
            
            if (roll < 90) {
                // Peluang 70%: Pilih acak 1 dari 3 arah ke depan (masing-masing peluang 1/3)
                targetDir = forwardDirs[rng.nextInt(forwardDirs.length)];
                rc.setIndicatorString("70% Area (Forward)");
            } else {
                // Peluang 30%: Pilih acak 1 dari 5 arah lainnya (masing-masing peluang 1/5)
                targetDir = otherDirs[rng.nextInt(otherDirs.length)];
                rc.setIndicatorString("30% Area (Opposite)");
            }
            hasAssignedDirection = true;
        }
        Direction moveDir = targetDir;
        if (!rc.canMove(moveDir)) {
            moveDir = getRandomValidMove(rc);
            hasAssignedDirection = false; 
        }
        
        // 5. Eksekusi Jalan -> TAMBAHKAN PENGECEKAN DI SINI!
        if (!isRefilling) {
            if (moveDir != null && rc.canMove(moveDir)) {
                rc.move(moveDir);
            }
        }
        // Try to paint beneath us as we walk to avoid paint penalties.
        // Avoiding wasting paint by re-painting our own tiles.
        MapInfo currentTile = rc.senseMapInfo(rc.getLocation());
        if (!currentTile.getPaint().isAlly() && rc.canAttack(rc.getLocation())){
            rc.attack(rc.getLocation());
        }
    }


    /**
     * Run a single turn for a Mopper.
     * This code is wrapped inside the infinite loop in run(), so it is called once per turn.
     */
    public static void runMopper(RobotController rc) throws GameActionException {
    
    // =========================================================
        // 1. SENSOR & PEMILIHAN TARGET (HUNTER MODE)
        // =========================================================
        RobotInfo[] nearbyEnemies = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        MapInfo[] nearbyTiles = rc.senseNearbyMapInfos();
        
        MapLocation targetLoc = null;
        
        // Prioritas Target 1: Robot/Tower Musuh
        if (nearbyEnemies.length > 0) {
            targetLoc = nearbyEnemies[0].getLocation();
            rc.setIndicatorString("Mengejar Musuh!");
        } 
        // Prioritas Target 2: Cat Musuh (Jika tidak ada robot terlihat)
        else {
            for (MapInfo tile : nearbyTiles) {
                if (tile.getPaint().isEnemy()) {
                    targetLoc = tile.getMapLocation();
                    rc.setIndicatorString("Mengejar Cat Musuh!");
                    break;
                }
            }
        }

        // =========================================================
        // 2. EKSEKUSI PERGERAKAN (GABUNGAN SOLDIER & HUNTER)
        // =========================================================
        Direction moveDir = null;

        if (targetLoc != null) {
            // JIKA ADA TARGET: Mode Hunter, langsung ambil jalan terpendek ke target!
            moveDir = rc.getLocation().directionTo(targetLoc);
            
            // Hapus ingatan jalan eksplorasi, agar nanti saat musuh habis, 
            // ia menghitung ulang arah tengah yang baru.
            hasAssignedDirection = false; 
        } 
        else {
            // JIKA TIDAK ADA TARGET: Mode Eksplorasi ala Soldier
            rc.setIndicatorString("Eksplorasi (Forward Wiggle)");
            
            if (!hasAssignedDirection) {
                MapLocation myLoc = rc.getLocation();
                MapLocation center = new MapLocation(rc.getMapWidth() / 2, rc.getMapHeight() / 2);
                Direction dirToCenter = myLoc.directionTo(center);
                
                if (dirToCenter == Direction.CENTER) {
                    dirToCenter = directions[rng.nextInt(8)];
                }
                
                // Pasti ke tengah atau serong kiri/kanan
                Direction[] forwardDirs = {
                    dirToCenter, 
                    dirToCenter.rotateLeft(), 
                    dirToCenter.rotateRight()
                };
                Direction[] otherDirs = {
                dirToCenter.rotateLeft().rotateLeft(),
                dirToCenter.rotateLeft().rotateLeft().rotateLeft(),
                dirToCenter.opposite(),
                dirToCenter.rotateRight().rotateRight(),
                dirToCenter.rotateRight().rotateRight().rotateRight()
            };
            
            // 3. Logika Probabilitas (Roll Dadu 0-99)
            int roll = rng.nextInt(100);
            
            if (roll < 90) {
                // Peluang 70%: Pilih acak 1 dari 3 arah ke depan (masing-masing peluang 1/3)
                targetDir = forwardDirs[rng.nextInt(forwardDirs.length)];
                rc.setIndicatorString("70% Area (Forward)");
            } else {
                // Peluang 30%: Pilih acak 1 dari 5 arah lainnya (masing-masing peluang 1/5)
                targetDir = otherDirs[rng.nextInt(otherDirs.length)];
                rc.setIndicatorString("30% Area (Opposite)");
            }
            hasAssignedDirection = true;
                
            }
            // Gunakan arah yang sudah disimpan di memori
            moveDir = targetDir;
        }

        // PENGAMAN JALAN: Jika arahnya nabrak, cari jalan lain dan lupakan arah lama
        if (moveDir == Direction.CENTER || !rc.canMove(moveDir)) {
            moveDir = getRandomValidMove(rc); 
            hasAssignedDirection = false; 
        }
        
        // EKSEKUSI JALAN
        if (moveDir != null && rc.canMove(moveDir)) {
            rc.move(moveDir);
        }

        // =========================================================
        // 3. LOGIKA EKSEKUSI SERANGAN & PEMBERSIHAN (SETELAH JALAN)
        // =========================================================
        MapLocation currentLoc = rc.getLocation();
        boolean hasSwung = false;

        // A. Prioritas 1: Mop Swing Area (KHUSUS UNTUK MENYERANG ROBOT/TOWER)
        for (Direction d : directions) {
            if (d == Direction.CENTER) continue;
            
            if (rc.canMopSwing(d)) {
                MapLocation checkLoc = currentLoc.add(d);
                RobotInfo robot = rc.senseRobotAtLocation(checkLoc);
                
                // Mop Swing HANYA dieksekusi jika ada robot/tower musuh, BUKAN untuk cat lantai
                if (robot != null && robot.getTeam() != rc.getTeam()) {
                    rc.mopSwing(d);
                    hasSwung = true;
                    System.out.println("Mop Swing menghantam musuh di arah " + d);
                    break;
                }
            }
        }

        // B. Prioritas 2: CLEAR PAINT (Mengepel Lantai / Menghapus Cat Musuh)
        // Ini kunci utama untuk membersihkan benteng/cat pelindung Tower musuh!
        if (!hasSwung) {
            // 1. Cek telapak kaki sendiri dulu agar tidak mati konyol karena penalti
            MapInfo feetInfo = rc.senseMapInfo(currentLoc);
            if (feetInfo.getPaint().isEnemy() && rc.canAttack(currentLoc)) {
                rc.attack(currentLoc);
                hasSwung = true;
                System.out.println("Membersihkan cat musuh di bawah kaki!");
            } 
            // 2. Jika kaki aman, pel cat musuh yang ada di sekitar (terutama di sekitar Tower)
            else {
                MapInfo[] attackableTiles = rc.senseNearbyMapInfos(rc.getType().actionRadiusSquared);
                for (MapInfo tile : attackableTiles) {
                    if (tile.getPaint().isEnemy() && rc.canAttack(tile.getMapLocation())) {
                        rc.attack(tile.getMapLocation());
                        hasSwung = true;
                        System.out.println("Mengepel cat musuh di " + tile.getMapLocation());
                        break;
                    }
                }
            }
        }

        // C. Prioritas 3: Menembak Musuh Jarak Jauh (Jika area sudah bersih dari cat)
        if (!hasSwung) {
            RobotInfo[] shootableEnemies = rc.senseNearbyRobots(rc.getType().actionRadiusSquared, rc.getTeam().opponent());
            if (shootableEnemies.length > 0) {
                MapLocation enemyLoc = shootableEnemies[0].getLocation();
                if (rc.canAttack(enemyLoc)) {
                    rc.attack(enemyLoc);
                    hasSwung = true;
                    System.out.println("Menembak musuh di " + enemyLoc);
                }
            }
        }

        // =========================================================
        // 4. TELEMETRI & BROADCAST
        // =========================================================
        updateEnemyRobots(rc);
}

    public static void updateEnemyRobots(RobotController rc) throws GameActionException{
        // Sensing methods can be passed in a radius of -1 to automatically 
        // use the largest possible value.
        RobotInfo[] enemyRobots = rc.senseNearbyRobots(-1, rc.getTeam().opponent());
        if (enemyRobots.length != 0){
            rc.setIndicatorString("There are nearby enemy robots! Scary!");
            // Save an array of locations with enemy robots in them for possible future use.
            MapLocation[] enemyLocations = new MapLocation[enemyRobots.length];
            for (int i = 0; i < enemyRobots.length; i++){
                enemyLocations[i] = enemyRobots[i].getLocation();
            }
            RobotInfo[] allyRobots = rc.senseNearbyRobots(-1, rc.getTeam());
            // Occasionally try to tell nearby allies how many enemy robots we see.
            if (rc.getRoundNum() % 20 == 0){
                for (RobotInfo ally : allyRobots){
                    if (rc.canSendMessage(ally.location, enemyRobots.length)){
                        rc.sendMessage(ally.location, enemyRobots.length);
                    }
                }
            }
        }
    }
}