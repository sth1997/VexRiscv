	.text
	.attribute	4, 16
	#.attribute	5, "rv32i2p0_set1p0"
	.attribute	5, "rv32i2p0"
	.file	"baz.c"
	.globl	clear                           # -- Begin function clear
	.p2align	2
	.type	clear,@function
clear:                                  # @clear
# %bb.0:                                # %entry
	addi	sp, sp, -16
	sw	ra, 12(sp)                      # 4-byte Folded Spill
	sw	s0, 8(sp)                       # 4-byte Folded Spill
	addi	s0, sp, 16
	li	a0, 0
	sw	a0, -12(s0)
	j	.LBB0_1
.LBB0_1:                                # %for.cond
                                        # =>This Inner Loop Header: Depth=1
	lw	a1, -12(s0)
	li	a0, 9
	blt	a0, a1, .LBB0_4
	j	.LBB0_2
.LBB0_2:                                # %for.body
                                        #   in Loop: Header=BB0_1 Depth=1
	lw	a0, -12(s0)
	slli	a1, a0, 2
	lui	a0, %hi(tmp)
	addi	a0, a0, %lo(tmp)
	add	a1, a0, a1
	li	a0, 0
	sw	a0, 0(a1)
	j	.LBB0_3
.LBB0_3:                                # %for.inc
                                        #   in Loop: Header=BB0_1 Depth=1
	lw	a0, -12(s0)
	addi	a0, a0, 1
	sw	a0, -12(s0)
	j	.LBB0_1
.LBB0_4:                                # %for.end
	lw	ra, 12(sp)                      # 4-byte Folded Reload
	lw	s0, 8(sp)                       # 4-byte Folded Reload
	addi	sp, sp, 16
	ret
.Lfunc_end0:
	.size	clear, .Lfunc_end0-clear
                                        # -- End function
	.globl	main                            # -- Begin function main
	.p2align	2
	.type	main,@function
main:                                   # @main
# %bb.0:                                # %entry
	addi	sp, sp, -48
	sw	ra, 44(sp)                      # 4-byte Folded Spill
	sw	s0, 40(sp)                      # 4-byte Folded Spill
	addi	s0, sp, 48
	li	a0, 0
	sw	a0, -12(s0)
	lui	a1, %hi(G)
	li	a5, 1
	sw	a5, %lo(G)(a1)
	addi	a2, a1, %lo(G)
	sw	a2, -36(s0)                     # 4-byte Folded Spill
	li	a6, 4
	sw	a6, 4(a2)
	li	a1, -1
	sw	a1, 8(a2)
	sw	a0, 20(a2)
	li	a4, 2
	sw	a4, 24(a2)
	li	a3, 3
	sw	a3, 28(a2)
	sw	a6, 32(a2)
	sw	a1, 36(a2)
	sw	a5, 40(a2)
	sw	a3, 44(a2)
	sw	a6, 48(a2)
	sw	a1, 52(a2)
	sw	a5, 60(a2)
	sw	a4, 64(a2)
	sw	a6, 68(a2)
	sw	a1, 72(a2)
	sw	a0, 80(a2)
	sw	a5, 84(a2)
	sw	a4, 88(a2)
	sw	a3, 92(a2)
	sw	a1, 96(a2)
	sw	a0, -16(s0)
	sw	a0, -20(s0)
	j	.LBB1_1
.LBB1_1:                                # %for.cond
                                        # =>This Loop Header: Depth=1
                                        #     Child Loop BB1_3 Depth 2
	lw	a1, -20(s0)
	li	a0, 4
	blt	a0, a1, .LBB1_8
	j	.LBB1_2
.LBB1_2:                                # %for.body
                                        #   in Loop: Header=BB1_1 Depth=1
	lw	a0, -20(s0)
	li	a1, 20
	call	__mulsi3@plt
	mv	a1, a0
	lui	a0, %hi(G)
	addi	a0, a0, %lo(G)
	add	a0, a0, a1
	#setcount	a0, a0
	nop
	sw	a0, -24(s0)
	li	a0, 0
	sw	a0, -28(s0)
	j	.LBB1_3
.LBB1_3:                                # %for.cond1
                                        #   Parent Loop BB1_1 Depth=1
                                        # =>  This Inner Loop Header: Depth=2
	lw	a0, -28(s0)
	lw	a1, -24(s0)
	bge	a0, a1, .LBB1_6
	j	.LBB1_4
.LBB1_4:                                # %for.body3
                                        #   in Loop: Header=BB1_3 Depth=2
	lw	a0, -20(s0)
	li	a1, 20
	sw	a1, -48(s0)                     # 4-byte Folded Spill
	call	__mulsi3@plt
	mv	a1, a0
	lui	a0, %hi(G)
	addi	a0, a0, %lo(G)
	sw	a0, -40(s0)                     # 4-byte Folded Spill
	add	a0, a0, a1
	lw	a1, -28(s0)
	slli	a1, a1, 2
	add	a0, a0, a1
	lw	a0, 0(a0)
	sw	a0, -32(s0)
	call	clear
	lw	a1, -48(s0)                     # 4-byte Folded Reload
	lw	a0, -20(s0)
	call	__mulsi3@plt
	lw	a1, -48(s0)                     # 4-byte Folded Reload
	mv	a2, a0
	lw	a0, -40(s0)                     # 4-byte Folded Reload
	add	a0, a0, a2
	sw	a0, -44(s0)                     # 4-byte Folded Spill
	lw	a0, -32(s0)
	call	__mulsi3@plt
	lw	a1, -44(s0)                     # 4-byte Folded Reload
	mv	a2, a0
	lw	a0, -40(s0)                     # 4-byte Folded Reload
	add	a2, a0, a2
	lui	a0, %hi(tmp)
	addi	a0, a0, %lo(tmp)
	#setinter	a0, a1, a2
	#setcount	a1, a0
	nop
	nop
	lw	a0, -16(s0)
	add	a0, a0, a1
	sw	a0, -16(s0)
	j	.LBB1_5
.LBB1_5:                                # %for.inc
                                        #   in Loop: Header=BB1_3 Depth=2
	lw	a0, -28(s0)
	addi	a0, a0, 1
	sw	a0, -28(s0)
	j	.LBB1_3
.LBB1_6:                                # %for.end
                                        #   in Loop: Header=BB1_1 Depth=1
	j	.LBB1_7
.LBB1_7:                                # %for.inc10
                                        #   in Loop: Header=BB1_1 Depth=1
	lw	a0, -20(s0)
	addi	a0, a0, 1
	sw	a0, -20(s0)
	j	.LBB1_1
.LBB1_8:                                # %for.end12
	lw	a0, -16(s0)
	lw	ra, 44(sp)                      # 4-byte Folded Reload
	lw	s0, 40(sp)                      # 4-byte Folded Reload
	addi	sp, sp, 48
	ret
.Lfunc_end1:
	.size	main, .Lfunc_end1-main
                                        # -- End function
	.globl	irqCallback                     # -- Begin function irqCallback
	.p2align	2
	.type	irqCallback,@function
irqCallback:                            # @irqCallback
# %bb.0:                                # %entry
	addi	sp, sp, -16
	sw	ra, 12(sp)                      # 4-byte Folded Spill
	sw	s0, 8(sp)                       # 4-byte Folded Spill
	addi	s0, sp, 16
	lw	ra, 12(sp)                      # 4-byte Folded Reload
	lw	s0, 8(sp)                       # 4-byte Folded Reload
	addi	sp, sp, 16
	ret
.Lfunc_end2:
	.size	irqCallback, .Lfunc_end2-irqCallback
                                        # -- End function
	.type	G,@object                       # @G
	.bss
	.globl	G
	.p2align	2
G:
	.zero	100
	.size	G, 100

	.type	tmp,@object                     # @tmp
	.globl	tmp
	.p2align	2
tmp:
	.zero	40
	.size	tmp, 40

	.ident	"clang version 15.0.0 (https://github.com/llvm/llvm-project.git 32d110b9eae0dffade27152b5bb14d1e95ab963a)"
	.section	".note.GNU-stack","",@progbits
	#.addrsig
	#.addrsig_sym clear
	#.addrsig_sym G
	#.addrsig_sym tmp
