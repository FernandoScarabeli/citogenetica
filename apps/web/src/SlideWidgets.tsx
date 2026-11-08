import { useEffect, useId, useRef, useState, type KeyboardEvent, type ReactNode } from 'react';
import { Check, ChevronDown, X } from 'lucide-react';
import { typeClass } from '@/lib/type-scale';

export type ProtocolOption = { value: string; label: string };
export function ProtocolSelect({value,onChange,options}: {value: string; onChange: (value: string)=>void; options: ProtocolOption[]}) {
 const [open,setOpen]=useState(false); const [active,setActive]=useState(0); const root=useRef<HTMLDivElement>(null); const trigger=useRef<HTMLButtonElement>(null); const id=useId();
 useEffect(()=>{if(!open)return;function outside(event: PointerEvent){if(!root.current?.contains(event.target as Node))setOpen(false);}document.addEventListener('pointerdown',outside);return ()=>document.removeEventListener('pointerdown',outside);},[open]);
 function show(){setActive(options.findIndex(option=>option.value===value));setOpen(true);}
 function choose(index:number){onChange(options[index].value);setOpen(false);trigger.current?.focus();}
 function onKeyDown(event:KeyboardEvent<HTMLButtonElement>){
  if(event.key==='Escape'&&open){event.preventDefault();event.stopPropagation();setOpen(false);return;}
  if(event.key==='Tab'){setOpen(false);return;}
  if(event.key==='ArrowDown'||event.key==='ArrowUp'){event.preventDefault();if(!open){show();return;}setActive(current=>(current+(event.key==='ArrowDown'?1:-1)+options.length)%options.length);return;}
  if(open&&(event.key==='Home'||event.key==='End')){event.preventDefault();setActive(event.key==='Home'?0:options.length-1);return;}
  if(open&&(event.key==='Enter'||event.key===' ')){event.preventDefault();choose(active);}
 }
 return <div className="protocol-field"><span className="protocol-label" id={`${id}-label`}>Protocolo</span><div className="protocol-select" ref={root}><button ref={trigger} type="button" className="protocol-trigger" role="combobox" aria-labelledby={`${id}-label ${id}-value`} aria-expanded={open} aria-controls={`${id}-options`} aria-activedescendant={open?`${id}-option-${active}`:undefined} aria-haspopup="listbox" onClick={()=>open?setOpen(false):show()} onKeyDown={onKeyDown}><span id={`${id}-value`}>{options.find(option=>option.value===value)?.label}</span><ChevronDown size={16} aria-hidden="true"/></button>{open&&<div className={`protocol-options${options.findIndex(option=>option.value===value)===options.length-1&&options.length>1?' opens-up':''}`} id={`${id}-options`} role="listbox" aria-labelledby={`${id}-label`}>{options.map((option,index)=><div id={`${id}-option-${index}`} key={option.value} role="option" aria-selected={option.value===value} className={`protocol-option${active===index?' is-active':''}`} onMouseEnter={()=>setActive(index)} onMouseDown={event=>event.preventDefault()} onClick={()=>choose(index)}><span>{option.label}</span>{option.value===value&&<Check size={16} aria-hidden="true"/>}</div>)}</div>}</div></div>;
}
export type SelectOption = { value: string; label: string };
export function EntrySelect({label,value,options,onChange,placeholder,invalid,controlRef}: {label:string; value:string; options:SelectOption[]; onChange:(value:string)=>void; placeholder?:string; invalid?:boolean; controlRef?:React.RefObject<HTMLButtonElement|null>}) {
 const [open,setOpen]=useState(false); const [active,setActive]=useState(0); const [opensUp,setOpensUp]=useState(false);
 const root=useRef<HTMLDivElement>(null); const localTrigger=useRef<HTMLButtonElement>(null); const trigger=controlRef||localTrigger; const id=useId();
 useEffect(()=>{if(!open)return;function outside(event:PointerEvent){if(!root.current?.contains(event.target as Node))setOpen(false);}document.addEventListener('pointerdown',outside);return ()=>document.removeEventListener('pointerdown',outside);},[open]);
 useEffect(()=>{if(open)document.getElementById(`${id}-option-${active}`)?.scrollIntoView({block:'nearest'});},[active,open,id]);
 function show(){const rect=trigger.current?.getBoundingClientRect();setOpensUp(!!rect && window.innerHeight-rect.bottom<Math.min(options.length*44+20,240) && rect.top>window.innerHeight-rect.bottom);setActive(Math.max(0,options.findIndex(option=>option.value===value)));setOpen(true);}
 function choose(index:number){const option=options[index];if(!option)return;onChange(option.value);setOpen(false);trigger.current?.focus();}
 function onKeyDown(event:KeyboardEvent<HTMLButtonElement>){
  if(event.key==='Escape'&&open){event.preventDefault();event.stopPropagation();setOpen(false);return;}
  if(event.key==='Tab'){setOpen(false);return;}
  if(event.key==='ArrowDown'||event.key==='ArrowUp'){event.preventDefault();if(!open){show();return;}setActive(current=>(current+(event.key==='ArrowDown'?1:-1)+options.length)%options.length);return;}
  if(open&&(event.key==='Home'||event.key==='End')){event.preventDefault();setActive(event.key==='Home'?0:options.length-1);return;}
  if(open&&(event.key==='Enter'||event.key===' ')){event.preventDefault();choose(active);return;}
  if(open&&event.key.length===1&&!event.ctrlKey&&!event.metaKey&&!event.altKey){const index=options.findIndex(option=>option.label.toLocaleLowerCase('pt-BR').startsWith(event.key.toLocaleLowerCase('pt-BR')));if(index>=0){event.preventDefault();setActive(index);}}
 }
 return <div className="protocol-field entry-select-field"><span className="protocol-label" id={`${id}-label`}>{label}</span><div className="protocol-select" ref={root}><button ref={trigger} type="button" className="protocol-trigger" role="combobox" aria-labelledby={`${id}-label ${id}-value`} aria-expanded={open} aria-controls={open?`${id}-options`:undefined} aria-activedescendant={open?`${id}-option-${active}`:undefined} aria-haspopup="listbox" aria-invalid={invalid||undefined} aria-describedby={invalid?`${id}-error`:undefined} onClick={()=>open?setOpen(false):show()} onKeyDown={onKeyDown}><span id={`${id}-value`} className={!value?'select-placeholder':undefined}>{options.find(option=>option.value===value)?.label||placeholder}</span><ChevronDown size={16} aria-hidden="true"/></button>{open&&<div className={`protocol-options entry-options${opensUp?' opens-up':''}`} id={`${id}-options`} role="listbox" aria-labelledby={`${id}-label`}>{options.map((option,index)=><div id={`${id}-option-${index}`} key={option.value} role="option" aria-selected={option.value===value} className={`protocol-option${active===index?' is-active':''}`} onMouseEnter={()=>setActive(index)} onMouseDown={event=>event.preventDefault()} onClick={()=>choose(index)}><span>{option.label}</span>{option.value===value&&<Check size={16} aria-hidden="true"/>}</div>)}</div>}</div>{invalid&&<span className="select-error" id={`${id}-error`} role="alert">Selecione uma anormalidade.</span>}</div>;
}
export function Modal({title,children,onClose}: {title:string;children:ReactNode;onClose:()=>void}) {
  const ref=useRef<HTMLDialogElement>(null);
  useEffect(()=>{ref.current?.showModal();},[]);
  return <dialog ref={ref} onClose={onClose} onClick={e=>{if(e.target===ref.current) onClose()}}><div className="modal-head"><h2 className={typeClass("title")}>{title}</h2><button className="icon-button" aria-label="Fechar" onClick={onClose}><X size={19}/></button></div>{children}</dialog>;
}

export type TypeChoice = { id: string; nome: string };

/** Seleção de uma ou mais anormalidades da fase, com a opção de descrever uma nova ("Outro"). */
export function TypeChecklist({legend,options,selected,onToggle,other,onOther,allowOther=true,otherText,onOtherText,error,firstRef}: {legend:string; options:TypeChoice[]; selected:string[]; onToggle:(id:string)=>void; other:boolean; onOther:(value:boolean)=>void; allowOther?:boolean; otherText:string; onOtherText:(value:string)=>void; error?:string; firstRef?:React.RefObject<HTMLInputElement|null>}) {
 const id=useId();
 return <fieldset className="type-checklist" aria-describedby={error?`${id}-error`:undefined} aria-invalid={error?true:undefined}>
  <legend>{legend}</legend>
  <div className="type-options">
   {options.map((option,index)=><label className="check-option" key={option.id}><input ref={index===0&&firstRef?firstRef:undefined} type="checkbox" checked={selected.includes(option.id)} onChange={()=>onToggle(option.id)}/><span>{option.nome}</span></label>)}
   {allowOther&&<label className="check-option"><input ref={options.length===0&&firstRef?firstRef:undefined} type="checkbox" checked={other} onChange={event=>onOther(event.target.checked)}/><span>Outro (especificar)</span></label>}
  </div>
  {allowOther&&other&&<label className="check-other">Descreva a anormalidade<input value={otherText} maxLength={120} onChange={event=>onOtherText(event.target.value)} placeholder="Nome da anormalidade"/></label>}
  {error&&<span className="select-error" id={`${id}-error`} role="alert">{error}</span>}
 </fieldset>;
}
