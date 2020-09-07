package dev.toastmc.client.module.combat

import dev.toastmc.client.ToastClient
import dev.toastmc.client.event.TickEvent
import dev.toastmc.client.module.Category
import dev.toastmc.client.module.Module
import dev.toastmc.client.module.ModuleManifest
import dev.toastmc.client.util.DamageUtil
import dev.toastmc.client.util.DamageUtil.getExplosionDamage
import dev.toastmc.client.util.InventoryUtils
import dev.toastmc.client.util.ItemUtil.isPickaxe
import io.github.fablabsmc.fablabs.api.fiber.v1.annotation.Setting
import me.zero.alpine.listener.EventHandler
import me.zero.alpine.listener.EventHook
import me.zero.alpine.listener.Listener
import net.minecraft.entity.Entity
import net.minecraft.entity.decoration.EndCrystalEntity
import net.minecraft.entity.effect.StatusEffects
import net.minecraft.item.Item
import net.minecraft.item.Items
import net.minecraft.item.SwordItem
import net.minecraft.item.ToolItem
import net.minecraft.util.Hand
import net.minecraft.util.math.BlockPos

@ModuleManifest(
        label = "CrystalAura",
        description = "Hit crystals Automatically",
        category = Category.COMBAT,
        aliases = ["crystal"]
)
class CrystalAura : Module() {
    @Setting(name = "Explode") var explode = true
    @Setting(name = "Place") var place = true
    @Setting(name = "Range") var range = 4
    @Setting(name = "MaxSelfDamage") var maxselfdamage = 0
    @Setting(name = "MaxBreaks") var maxbreaks = 2
    @Setting(name = "AutoSwitch") var autoswitch = true
    @Setting(name = "AntiWeakness") var antiweakness = true
    @Setting(name = "IgnoreEating") var ignoreeating = true
    @Setting(name = "IgnorePickaxe") var ignorepickaxe = true
    @Setting(name = "Players") var players = true
    @Setting(name = "Mobs") var mobs = true
    @Setting(name = "Animals") var animals = true
    @Setting(name = "Rotate") var rotate = true

    private val blackList = HashMap<BlockPos, Int>()

    private var oldSlot = -1
    private var newSlot = 0
    private var crystalSlot = 0
    private var breaks = 0

    override fun onEnable() {
        if (mc.player == null) return
        ToastClient.EVENT_BUS.subscribe(onTickEvent)
    }

    override fun onDisable() {
        if (mc.player == null) return
        ToastClient.EVENT_BUS.unsubscribe(onTickEvent)
    }

    @EventHandler
    private val onTickEvent = Listener(EventHook<TickEvent.Client.InGame> {
        val damageCache = DamageUtil.getDamageCache(); damageCache.clear()
        var shortestDistance: Double? = null
        var crystal: EndCrystalEntity? = null
        for (entity in mc.world!!.entities) {
            if (entity == null || entity.removed || entity !is EndCrystalEntity) continue
            val p = entity.blockPos.down()
            if (blackList.containsKey(p)) {
                if (blackList[p]!! > 0) blackList.replace(p, blackList[p]!! - 1) else blackList.remove(p)
            }
            val distance = mc.player!!.distanceTo(entity)
            if (shortestDistance == null || distance < shortestDistance) {
                shortestDistance = distance.toDouble()
                crystal = entity
            }
        }
        if (crystal == null) {
            return@EventHook
        }
        val offhand = mc.player!!.offHandStack.item === Items.END_CRYSTAL
        crystalSlot = if (InventoryUtils.getSlotsHotbar(Item.getRawId(Items.END_CRYSTAL)) != null) InventoryUtils.getSlotsHotbar(Item.getRawId(Items.END_CRYSTAL))!![0] else -1
        if (explodeCheck(crystal)) {
            oldSlot = mc.player!!.inventory.selectedSlot
            when {
                breaks >= maxbreaks -> {
                    rotate = false
                    breaks = 0
                    return@EventHook
                }
                mc.player!!.inventory.mainHandStack.isFood && ignoreeating -> return@EventHook
                isPickaxe(mc.player!!.inventory.mainHandStack.item) && ignorepickaxe -> return@EventHook
                autoswitch && !mc.player!!.hasStatusEffect(StatusEffects.WEAKNESS) && !offhand ->  mc.player!!.inventory.selectedSlot = crystalSlot
                mc.player!!.hasStatusEffect(StatusEffects.WEAKNESS) && antiweakness -> {
                    newSlot = -1
                    if (mc.player!!.inventory.mainHandStack.item !is ToolItem || mc.player!!.inventory.mainHandStack.item !is SwordItem) {
                        for (l in 0..8) {
                            if (mc.player!!.inventory.getStack(l).item is ToolItem || mc.player!!.inventory.getStack(l).item is SwordItem) {
                                newSlot = l
                                break
                            }
                        }
                    } else {
                        mc.interactionManager?.attackEntity(mc.player, crystal)
                        mc.player!!.swingHand(Hand.MAIN_HAND)
                        if(autoswitch) mc.player!!.inventory.selectedSlot = crystalSlot
                    }
                    if (newSlot != -1) {
                        mc.player!!.inventory.selectedSlot = newSlot
                    }
                }
            }
            mc.interactionManager?.attackEntity(mc.player, crystal)
            mc.player!!.swingHand(if(!offhand) Hand.MAIN_HAND else Hand.OFF_HAND)
            ++breaks
        } else { // Failed explodeCheck
            rotate = false
            if (oldSlot != -1) {
                mc.player?.inventory?.selectedSlot = oldSlot
                oldSlot = -1
            }
        }
    })

    private fun explodeCheck(entity: Entity) : Boolean {
        val p = mc.player!!
        val damageSafe = getExplosionDamage(entity.blockPos, p) - p.health <= maxselfdamage - 1 || p.isInvulnerable || p.isCreative || p.isSpectator
        return damageSafe && explode && mc.player!!.distanceTo(entity) <= range
    }
}