type BrandDictionary = Record<string, string>;

const spanishDictionary: BrandDictionary = {
  'ELYSIUM VANGUARD': 'ELYSIUM VANGUARD',
  'Ecosistema Automotriz • Vanguard Network': 'Ecosistema Automotriz • Vanguard Network',
  'Datos de OBD2 sincronizados desde la App MEET': 'Datos de OBD2 sincronizados desde la App MEET',
  'Bienvenido a MEET': 'Bienvenido a MEET',
};

export function useBrand() {
  return {
    brandName: 'Elysium Vanguard',
    productName: 'MEET',
    t(value: string) {
      return spanishDictionary[value] ?? value;
    },
  };
}
