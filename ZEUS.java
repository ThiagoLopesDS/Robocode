package roboteste;
import robocode.*;
import robocode.util.Utils;
import java.awt.geom.Point2D;
import java.awt.Color;
import java.util.Random;

/**
 * Robo com movimento aleatório + GuessFactor Targeting
 */
public class ZEUS extends AdvancedRobot {

    private double enemyEnergy = 100;
    private int moveDirection = 1;
    private Random random = new Random();

    // Configurações para GuessFactor
    private static final int BINS = 31;
    private static final double MAX_ESCAPE_ANGLE = Math.asin(8.0 / 20.0);
    private int[] gfStats = new int[BINS];
    private double enemyAbsoluteBearing;
    private double enemyDistance;
    private double enemyLateralVelocity;

    public void run() {
        setColors(Color.BLACK, Color.CYAN, Color.ORANGE);

        setAdjustGunForRobotTurn(true);
        setAdjustRadarForGunTurn(true);

        while (true) {
            // Radar infinito para travar no inimigo
            turnRadarRightRadians(Double.POSITIVE_INFINITY);
        }
    }

    public void onScannedRobot(ScannedRobotEvent e) {
        // --- Movimento Aleatório ---
        double change = enemyEnergy - e.getEnergy();
        if (change > 0 && change <= 3) {
            moveDirection = -moveDirection;
            setAhead((100 + random.nextInt(100)) * moveDirection);
        }
        enemyEnergy = e.getEnergy();

        // Gira perpendicular ao inimigo
        setTurnRight(e.getBearing() + 90 - 30 * moveDirection);

        // Salva dados para o tiro
        enemyAbsoluteBearing = getHeadingRadians() + e.getBearingRadians();
        enemyDistance = e.getDistance();
        enemyLateralVelocity = e.getVelocity() * Math.sin(e.getHeadingRadians() - enemyAbsoluteBearing);

        // Faz o tiro com GuessFactor
        doGuessFactorFire(e);

        // Mantém radar travado
        double radarTurn = Utils.normalRelativeAngle(enemyAbsoluteBearing - getRadarHeadingRadians());
        setTurnRadarRightRadians(2 * radarTurn);
    }

    private void doGuessFactorFire(ScannedRobotEvent e) {
        if (getGunHeat() > 0) return;

        double bulletPower = Math.min(3, Math.max(1, 400 / enemyDistance));
        double bulletSpeed = 20 - 3 * bulletPower;
        int currentBin = (BINS - 1) / 2;

        // Procura o bin mais provável (histograma aprendido)
        int bestBin = currentBin;
        for (int i = 0; i < BINS; i++) {
            if (gfStats[i] > gfStats[bestBin]) {
                bestBin = i;
            }
        }

        double guessFactor = (double)(bestBin - (BINS - 1) / 2) / ((BINS - 1) / 2);
        double angleOffset = guessFactor * MAX_ESCAPE_ANGLE * (enemyLateralVelocity >= 0 ? 1 : -1);
        double gunTurn = Utils.normalRelativeAngle(enemyAbsoluteBearing + angleOffset - getGunHeadingRadians());

        setTurnGunRightRadians(gunTurn);

        if (getGunHeat() == 0 && Math.abs(getGunTurnRemaining()) < Math.toRadians(5)) {
            setFire(bulletPower);
        }
    }

    // --- NOVO: Atualiza o histograma quando acertar um tiro ---
    public void onBulletHit(BulletHitEvent e) {
        double bulletBearing = Utils.normalRelativeAngle(
            e.getBullet().getHeadingRadians() - enemyAbsoluteBearing
        );

        int currentBin = (BINS - 1) / 2;
        int hitBin = (int) Math.round(
            (bulletBearing / MAX_ESCAPE_ANGLE) * currentBin + currentBin
        );

        if (hitBin >= 0 && hitBin < BINS) {
            gfStats[hitBin]++;
        }
    }

    public void onHitByBullet(HitByBulletEvent e) {
        // Reage mudando direção para evitar padrões
        moveDirection = -moveDirection;
        setAhead((50 + random.nextInt(100)) * moveDirection);
    }

    public void onHitWall(HitWallEvent e) {
        moveDirection = -moveDirection;
        setAhead(100 * moveDirection);
    }
}

