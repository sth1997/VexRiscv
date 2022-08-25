	.text
	.globl	_start                            # -- Begin function func
	.p2align	1
_start:                                   # @func
# %bb.0:                                # %entry
	li	a0, 0
	li	a1, 0
	li	a2, 0
	lui	a3, %hi(a)
	addi	a7, a3, %lo(a)
	lui	a3, %hi(b)
	addi	a4, a3, %lo(b)
	lui	a3, %hi(c)
	addi	a6, a3, %lo(c)
	j	.LBB0_3
.LBB0_1:                                # %if.then4
                                        #   in Loop: Header=BB0_3 Depth=1
	slli	a3, a0, 2
	add	a3, a3, a6
	sw	a5, 0(a3)
	addi	a2, a2, 1
	addi	a1, a1, 1
	addi	a0, a0, 1
.LBB0_2:                                # %if.end17
                                        #   in Loop: Header=BB0_3 Depth=1
	addi	a3, a2, -3
	seqz	a3, a3
	addi	a5, a1, -4
	seqz	a5, a5
	or	a3, a3, a5
	bnez	a3, .LBB0_7
.LBB0_3:                                # %if.end
                                        # =>This Inner Loop Header: Depth=1
	slli	a3, a2, 2
	add	a3, a3, a7
	lw	a5, 0(a3)
	slli	a3, a1, 2
	add	a3, a3, a4
	lw	a3, 0(a3)
	beq	a5, a3, .LBB0_1
# %bb.4:                                # %if.else
                                        #   in Loop: Header=BB0_3 Depth=1
	bge	a5, a3, .LBB0_6
# %bb.5:                                # %if.then12
                                        #   in Loop: Header=BB0_3 Depth=1
	addi	a2, a2, 1
	j	.LBB0_2
.LBB0_6:                                # %if.else14
                                        #   in Loop: Header=BB0_3 Depth=1
	addi	a1, a1, 1
	j	.LBB0_2
.LBB0_7:                                # %while.end
	lui	a1, %hi(cnt)
	sw	a0, %lo(cnt)(a1)
_loop:
	j _loop
                                        # -- End function

	.type	a,@object                       # @a
	.data
	.globl	a
	.p2align	2
a:
	.word	1                               # 0x1
	.word	2                               # 0x2
	.word	3                               # 0x3
	.size	a, 12

	.type	b,@object                       # @b
	.globl	b
	.p2align	2
b:
	.word	2                               # 0x2
	.word	3                               # 0x3
	.word	4                               # 0x4
	.word	5                               # 0x5
	.size	b, 16

	.type	c,@object                       # @c
	.bss
	.globl	c
	.p2align	2
c:
	.zero	40
	.size	c, 40

	.type	cnt,@object                     # @cnt
	.section	.sbss,"aw",@nobits
	.globl	cnt
	.p2align	2
cnt:
	.word	0                               # 0x0
	.size	cnt, 4

	.ident	"clang version 15.0.0 (git@github.com:ivwumupy/llvm-project.git f615eae4d1213e645ea6683dcb02d48d42df0c24)"
	.section	".note.GNU-stack","",@progbits
	.addrsig
