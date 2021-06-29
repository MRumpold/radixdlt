extern crate bytebuffer;

use bytebuffer::ByteBuffer;
use std::fmt;

use crate::substates::*;
use crate::types::Bytes;
use crate::types::Signature;
use crate::types::SubstateId;

pub struct Transaction {
    pub instructions: Vec<Instruction>,
}

#[derive(Debug)]
pub enum Instruction {
    HEADER(u8, u8),

    SYSCALL(Bytes),

    UP(Box<dyn Substate>),

    VDOWN(Box<dyn Substate>),

    VDOWNARG(Box<dyn Substate>, Bytes),

    DOWN(SubstateId),

    LDOWN(u32),

    DOWNALL(u8),

    MSG(Bytes),

    SIG(Signature),

    DOWNINDEX(Bytes),

    LREAD(u32),

    VREAD(Box<dyn Substate>),

    READ(SubstateId),

    END,
}

impl Transaction {
    pub fn from_bytes(bytes: Vec<u8>) -> Self {
        let mut instructions = Vec::new();
        let mut buffer = ByteBuffer::from_bytes(&bytes[..]);
        while buffer.get_rpos() < buffer.get_wpos() {
            let inst = Instruction::from_buffer(&mut buffer);
            // println!("{:?}", inst);
            instructions.push(inst);
        }
        Self { instructions }
    }
}

impl fmt::Debug for Transaction {
    fn fmt(&self, f: &mut fmt::Formatter<'_>) -> fmt::Result {
        write!(f, "Instructions:\n").unwrap();
        self.instructions
            .iter()
            .for_each(|i| write!(f, "|- {:?}\n", i).unwrap());
        fmt::Result::Ok(())
    }
}

impl Instruction {
    pub fn from_buffer(buffer: &mut ByteBuffer) -> Self {
        let t = buffer.read_u8();
        match t {
            0x00 => Self::END,
            0x01 => Self::UP(Self::read_substate(buffer)),
            0x02 => Self::VDOWN(Self::read_substate(buffer)),
            0x03 => Self::VDOWNARG(Self::read_substate(buffer), Bytes::from_buffer(buffer)),
            0x04 => Self::DOWN(SubstateId::from_buffer(buffer)),
            0x05 => Self::LDOWN(buffer.read_u32()),
            0x06 => Self::MSG(Bytes::from_buffer(buffer)),
            0x07 => Self::SIG(Signature::from_buffer(buffer)),
            0x08 => Self::DOWNALL(buffer.read_u8()),
            0x09 => Self::SYSCALL(Bytes::from_buffer(buffer)),
            0x0A => Self::HEADER(buffer.read_u8(), buffer.read_u8()),
            0x0B => Self::DOWNINDEX(Bytes::from_buffer(buffer)),
            0x0C => Self::LREAD(buffer.read_u32()),
            0x0D => Self::VREAD(Self::read_substate(buffer)),
            0x0E => Self::READ(SubstateId::from_buffer(buffer)),
            _ => panic!("Unexpected opcode: {}", t),
        }
    }

    fn read_substate(buffer: &mut ByteBuffer) -> Box<dyn Substate> {
        let t = buffer.read_u8();
        match t {
            0x00 => Box::new(REAddress::from_buffer(buffer)),
            0x03 => Box::new(TokenDefinition::from_buffer(buffer)),
            0x04 => Box::new(Tokens::from_buffer(buffer)),
            0x05 => Box::new(PreparedStake::from_buffer(buffer)),
            0x06 => Box::new(StakeOwnership::from_buffer(buffer)),
            0x07 => Box::new(PreparedUnstake::from_buffer(buffer)),
            0x08 => Box::new(ExitingStake::from_buffer(buffer)),
            0x0C => Box::new(ValidatorAllowDelegationFlag::from_buffer(buffer)),
            0x0D => Box::new(ValidatorRegisteredFlagCopy::from_buffer(buffer)),
            0x0E => Box::new(PreparedRegisteredFlagUpdate::from_buffer(buffer)),
            0x11 => Box::new(ValidatorOwnerCopy::from_buffer(buffer)),
            _ => panic!("Unsupported substate type: {}", t),
        }
    }
}

#[cfg(test)]
mod tests {
    use crate::transaction::Transaction;
    use std::fs;

    #[test]
    fn token_create() {
        let contents = fs::read_to_string("../samples/token_create.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn token_mint() {
        let contents = fs::read_to_string("../samples/token_mint.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn token_transfer() {
        let contents = fs::read_to_string("../samples/token_transfer.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn token_burn() {
        let contents = fs::read_to_string("../samples/token_burn.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn xrd_stake() {
        for n in 1..3 {
            let contents = fs::read_to_string(format!("../samples/xrd_stake{}.txt", n)).unwrap();
            let raw = hex::decode(contents).unwrap();
            let tx = Transaction::from_bytes(raw);
            println!("{:?}", tx)
        }
    }

    #[test]
    fn xrd_unstake() {
        for n in 1..3 {
            let contents = fs::read_to_string(format!("../samples/xrd_unstake{}.txt", n)).unwrap();
            let raw = hex::decode(contents).unwrap();
            let tx = Transaction::from_bytes(raw);
            println!("{:?}", tx)
        }
    }

    #[test]
    fn validator_register() {
        let contents = fs::read_to_string("../samples/validator_register.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }

    #[test]
    fn validator_unregister() {
        let contents = fs::read_to_string("../samples/validator_unregister.txt").unwrap();
        let raw = hex::decode(contents).unwrap();
        let tx = Transaction::from_bytes(raw);
        println!("{:?}", tx)
    }
}
