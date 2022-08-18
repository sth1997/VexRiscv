	.text
	.attribute	4, 16
	.globl _start
	.p2align	1
_start:
# %bb.0:                                # %entry
	addi	sp, sp, -16
	sw	ra, 12(sp)                      # 4-byte Folded Spill
	sw	s0, 8(sp)                       # 4-byte Folded Spill
	addi	s0, sp, 16
	lui	a0, %hi(a)
	addi	a0, a0, %lo(a)
	li	a1, 3
	li	a2, 0
	setload	a2, a0, a1
	lui	a0, %hi(b)
	addi	a0, a0, %lo(b)
	li	a3, 4
	li	a1, 1
	setload	a1, a0, a3
	lui	a0, %hi(c)
	addi	a3, a0, %lo(c)
	li	a0, 2
	setload	a0, a3, a2
	setinter	a0, a1, a2
        setfree a1
        setfree a2
        nop
        nop
        nop
        setcount        a1, a0
        setfree a0
_loop:
	j _loop

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

	.ident	"clang version 15.0.0 (git@github.com:ivwumupy/llvm-project.git f615eae4d1213e645ea6683dcb02d48d42df0c24)"
	.section	".note.GNU-stack","",@progbits
	.addrsig
	.addrsig_sym a
	.addrsig_sym b
	.addrsig_sym c
