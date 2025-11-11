package kim.biryeong.ttt.player.duck;

/**
 * 자폭 폭탄 관련 데이터. 플레이어가 모두 가지고 있음
 */
public class SuicideBombInfo {
    private int ticks = 0;
    private boolean alreadyExloded = false;
    private boolean triggered = false;

    public void setTick(int explodesAfterTick) {
        if (this.alreadyExloded) {
            return;
        }
        this.triggered = true;
        this.ticks = explodesAfterTick;
    }

    /**
     * returns remaining ticks to explode.
     * if already exploded, returns -1.
     * if not triggered yet, returns 0.
     * @return time to explode.
     */
    public int getRemainingTicksToExplode() {
        if (this.alreadyExloded) {
            return -1;
        }

        if (!this.triggered) {
            return -1;
        }

        return this.ticks;
    }

    public boolean tick() {
        if (alreadyExloded || !triggered) {
            return false;
        }

        if (this.ticks ==  0) {
            alreadyExloded = true;
            return true;
        }

        this.ticks--;
        return false;
    }

    public boolean isTriggered() {
        return this.triggered;
    }

    public void clearFuse() {
        this.triggered = false;
        this.ticks = 0;
        this.alreadyExloded = false;
    }
}
